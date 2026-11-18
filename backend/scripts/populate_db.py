#!/usr/bin/env python3
"""
populate_db.py - Instakill Database Populator
Fills the database with configurable test data according to Instakill's schema.
Supports both SQLite and PostgreSQL backends via a common DatabaseBackend interface.
Reads configuration from a YAML file (defaults to populate_config.yaml).
"""

import argparse
import datetime
import os
import random
import uuid
import bcrypt
import faker
from abc import ABC, abstractmethod

try:
    import yaml
except ImportError:
    yaml = None


DEFAULT_CONFIG_FILE = "populate_config.yaml"

DEFAULT_CONFIG = {
    # SQLite (legacy) settings
    "db_path": "instakill.db",
    # PostgreSQL settings
    "pg_host": "localhost",
    "pg_port": 5432,
    "pg_dbname": "instakill",
    "pg_user": "instakill",
    "pg_password": "instakill",
    # Backend selection: "sqlite" or "postgres"
    "backend": "sqlite",
    # Population settings
    "total_users": 10000,
    "min_posts_per_user": 10,
    "max_posts_per_user": 20,
    "min_likes_per_post": 0,
    "max_likes_per_post": 20,
    "min_comments_per_post": 0,
    "max_comments_per_post": 10,
    "min_words_per_post": 1,
    "max_words_per_post": 10,
    "days_back": 7,
    "batch_size": 10000,
    "seed": None,
    "skip_cleanup": False,
    "vacuum": False,
    "dry_run": False,
}


# ---------------------------------------------------------------------------
# YAML config loader
# ---------------------------------------------------------------------------

def load_yaml_config(file_path):
    if not os.path.exists(file_path):
        return {}

    if yaml is not None:
        with open(file_path, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f)
            return data if isinstance(data, dict) else {}

    # Basic fallback parser if PyYAML is not present
    parsed = {}
    with open(file_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or ":" not in line:
                continue
            key, val = line.split(":", 1)
            key = key.strip()
            val = val.split("#")[0].strip()
            if not val or val.lower() == "null":
                parsed[key] = None
            elif val.lower() == "true":
                parsed[key] = True
            elif val.lower() == "false":
                parsed[key] = False
            elif (val.startswith('"') and val.endswith('"')) or (val.startswith("'") and val.endswith("'")):
                parsed[key] = val[1:-1]
            else:
                try:
                    if "." in val:
                        parsed[key] = float(val)
                    else:
                        parsed[key] = int(val)
                except ValueError:
                    parsed[key] = val
    return parsed


# ---------------------------------------------------------------------------
# DatabaseBackend interface
# ---------------------------------------------------------------------------

class DatabaseBackend(ABC):
    """
    Abstract interface for database backends.
    Implementations handle DDL, DML, and connection lifecycle.
    """

    @abstractmethod
    def connect(self):
        """Establish connection and return self for chaining."""

    @abstractmethod
    def ensure_tables_exist(self):
        """Create tables and indexes if they do not exist."""

    @abstractmethod
    def cleanup(self):
        """Delete all data except the seed users (alice, bob)."""

    @abstractmethod
    def vacuum(self):
        """Reclaim disk space (no-op for backends that don't support it)."""

    @abstractmethod
    def get_existing_seed_users(self):
        """Return dict {username: id} for the seed users already in the DB."""

    @abstractmethod
    def upsert_seed_user_password(self, user_id: str, hashed_password: str):
        """Update the password hash for an existing seed user (e.g. charlie)."""

    @abstractmethod
    def insert_users(self, rows: list):
        """Bulk-insert user rows: (id, created_at, email, password_hash, username)."""

    @abstractmethod
    def insert_posts(self, rows: list):
        """Bulk-insert post rows: (id, author_id, comment_count, content, created_at, image_url, like_count, updated_at)."""

    @abstractmethod
    def insert_likes(self, rows: list):
        """Bulk-insert like rows: (id, created_at, post_id, user_id)."""

    @abstractmethod
    def insert_comments(self, rows: list):
        """Bulk-insert comment rows: (id, author_id, content, created_at, post_id)."""

    @abstractmethod
    def commit(self):
        """Commit the current transaction."""

    @abstractmethod
    def rollback(self):
        """Roll back the current transaction."""

    @abstractmethod
    def close(self):
        """Close the connection."""

    @abstractmethod
    def label(self) -> str:
        """Human-readable label for log messages."""


# ---------------------------------------------------------------------------
# SQLite backend
# ---------------------------------------------------------------------------

class SQLiteBackend(DatabaseBackend):
    def __init__(self, db_path: str):
        self.db_path = db_path
        self.con = None
        self.cur = None

    def label(self) -> str:
        return f"SQLite ({os.path.abspath(self.db_path)})"

    def connect(self):
        import sqlite3
        db_dir = os.path.dirname(os.path.abspath(self.db_path))
        if db_dir:
            os.makedirs(db_dir, exist_ok=True)
        self.con = sqlite3.connect(self.db_path)
        self.cur = self.con.cursor()
        # Speed-up pragmas for bulk insert
        self.cur.execute("PRAGMA synchronous = OFF")
        self.cur.execute("PRAGMA journal_mode = MEMORY")
        return self

    def ensure_tables_exist(self):
        self.cur.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id char(36) not null,
            created_at timestamp not null,
            email varchar(255) not null unique,
            password_hash varchar(255) not null,
            username varchar(255) not null unique,
            primary key (id)
        );
        """)
        self.cur.execute("""
        CREATE TABLE IF NOT EXISTS posts (
            id char(36) not null,
            author_id char(36) not null,
            comment_count bigint not null,
            content varchar(1000) not null,
            created_at timestamp not null,
            image_url varchar(2048),
            like_count bigint not null,
            updated_at timestamp not null,
            primary key (id),
            foreign key (author_id) references users (id)
        );
        """)
        self.cur.execute("""
        CREATE TABLE IF NOT EXISTS likes (
            id char(36) not null,
            created_at timestamp not null,
            post_id char(36) not null,
            user_id char(36) not null,
            primary key (id),
            foreign key (post_id) references posts (id),
            foreign key (user_id) references users (id)
        );
        """)
        self.cur.execute("""
        CREATE TABLE IF NOT EXISTS comments (
            id char(36) not null,
            author_id char(36) not null,
            content varchar(500) not null,
            created_at timestamp not null,
            post_id char(36) not null,
            primary key (id),
            foreign key (post_id) references posts (id),
            foreign key (author_id) references users (id)
        );
        """)
        self.cur.execute("CREATE INDEX IF NOT EXISTS idx_posts_author ON posts (author_id);")
        self.cur.execute("CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts (created_at);")
        self.cur.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_likes_post_user ON likes (post_id, user_id);")
        self.cur.execute("CREATE INDEX IF NOT EXISTS idx_comments_post ON comments (post_id);")

    def cleanup(self):
        self.cur.execute("DELETE FROM comments")
        self.cur.execute("DELETE FROM likes")
        self.cur.execute("DELETE FROM posts")
        self.cur.execute("DELETE FROM users WHERE username NOT IN ('alice', 'bob')")
        self.con.commit()

    def vacuum(self):
        self.cur.execute("VACUUM")
        self.con.commit()

    def get_existing_seed_users(self):
        self.cur.execute("SELECT id, username FROM users WHERE username IN ('alice', 'bob')")
        return {row[1]: row[0] for row in self.cur.fetchall()}

    def upsert_seed_user_password(self, user_id: str, hashed_password: str):
        self.cur.execute(
            "UPDATE users SET password_hash = ? WHERE id = ?",
            (hashed_password, user_id)
        )

    def _get_user_by_username(self, username: str):
        self.cur.execute("SELECT id FROM users WHERE username = ?", (username,))
        return self.cur.fetchone()

    def insert_users(self, rows: list):
        self.cur.executemany(
            "INSERT INTO users (id, created_at, email, password_hash, username) VALUES (?, ?, ?, ?, ?)",
            rows
        )

    def insert_posts(self, rows: list):
        self.cur.executemany(
            "INSERT INTO posts (id, author_id, comment_count, content, created_at, image_url, like_count, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            rows
        )

    def insert_likes(self, rows: list):
        self.cur.executemany(
            "INSERT INTO likes (id, created_at, post_id, user_id) VALUES (?, ?, ?, ?)",
            rows
        )

    def insert_comments(self, rows: list):
        self.cur.executemany(
            "INSERT INTO comments (id, author_id, content, created_at, post_id) VALUES (?, ?, ?, ?, ?)",
            rows
        )

    def commit(self):
        self.con.commit()

    def rollback(self):
        self.con.rollback()

    def close(self):
        if self.cur:
            self.cur.execute("PRAGMA synchronous = NORMAL")
        if self.con:
            self.con.close()


# ---------------------------------------------------------------------------
# PostgreSQL backend
# ---------------------------------------------------------------------------

class PostgreSQLBackend(DatabaseBackend):
    def __init__(self, host: str, port: int, dbname: str, user: str, password: str):
        self.host = host
        self.port = port
        self.dbname = dbname
        self.user = user
        self.password = password
        self.con = None
        self.cur = None

    def label(self) -> str:
        return f"PostgreSQL ({self.user}@{self.host}:{self.port}/{self.dbname})"

    def connect(self):
        try:
            import psycopg2
            import psycopg2.extras
        except ImportError:
            raise RuntimeError(
                "psycopg2 is not installed. Run: pip install psycopg2-binary"
            )
        self.con = psycopg2.connect(
            host=self.host,
            port=self.port,
            dbname=self.dbname,
            user=self.user,
            password=self.password,
        )
        self.con.autocommit = False
        self.cur = self.con.cursor()
        return self

    def ensure_tables_exist(self):
        self.cur.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id varchar(36) NOT NULL,
            created_at bigint NOT NULL,
            email varchar(255) NOT NULL,
            password_hash varchar(255) NOT NULL,
            username varchar(255) NOT NULL,
            PRIMARY KEY (id),
            CONSTRAINT users_email_key UNIQUE (email),
            CONSTRAINT users_username_key UNIQUE (username)
        );
        """)
        self.cur.execute("""
        CREATE TABLE IF NOT EXISTS posts (
            id varchar(36) NOT NULL,
            author_id varchar(36) NOT NULL,
            comment_count bigint NOT NULL,
            content varchar(1000) NOT NULL,
            created_at bigint NOT NULL,
            image_url varchar(2048),
            like_count bigint NOT NULL,
            updated_at bigint NOT NULL,
            PRIMARY KEY (id),
            CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES users (id)
        );
        """)
        self.cur.execute("""
        CREATE TABLE IF NOT EXISTS likes (
            id varchar(36) NOT NULL,
            created_at bigint NOT NULL,
            post_id varchar(36) NOT NULL,
            user_id varchar(36) NOT NULL,
            PRIMARY KEY (id),
            CONSTRAINT fk_likes_post FOREIGN KEY (post_id) REFERENCES posts (id),
            CONSTRAINT fk_likes_user FOREIGN KEY (user_id) REFERENCES users (id)
        );
        """)
        self.cur.execute("""
        CREATE TABLE IF NOT EXISTS comments (
            id varchar(36) NOT NULL,
            author_id varchar(36) NOT NULL,
            content varchar(500) NOT NULL,
            created_at bigint NOT NULL,
            post_id varchar(36) NOT NULL,
            PRIMARY KEY (id),
            CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts (id),
            CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users (id)
        );
        """)
        # Indexes (IF NOT EXISTS is supported since PG 9.5)
        self.cur.execute("CREATE INDEX IF NOT EXISTS idx_posts_author ON posts (author_id);")
        self.cur.execute("CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts (created_at);")
        self.cur.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_likes_post_user ON likes (post_id, user_id);")
        self.cur.execute("CREATE INDEX IF NOT EXISTS idx_comments_post ON comments (post_id);")
        self.con.commit()

    def cleanup(self):
        self.cur.execute("DELETE FROM comments")
        self.cur.execute("DELETE FROM likes")
        self.cur.execute("DELETE FROM posts")
        self.cur.execute("DELETE FROM users WHERE username NOT IN ('alice', 'bob')")
        self.con.commit()

    def vacuum(self):
        # VACUUM cannot run inside a transaction block in PostgreSQL
        old_autocommit = self.con.autocommit
        self.con.autocommit = True
        self.cur.execute("VACUUM ANALYZE")
        self.con.autocommit = old_autocommit

    def get_existing_seed_users(self):
        self.cur.execute("SELECT id, username FROM users WHERE username IN ('alice', 'bob')")
        return {row[1]: row[0] for row in self.cur.fetchall()}

    def upsert_seed_user_password(self, user_id: str, hashed_password: str):
        self.cur.execute(
            "UPDATE users SET password_hash = %s WHERE id = %s",
            (hashed_password, user_id)
        )

    def _get_user_by_username(self, username: str):
        self.cur.execute("SELECT id FROM users WHERE username = %s", (username,))
        return self.cur.fetchone()

    def insert_users(self, rows: list):
        import psycopg2.extras
        psycopg2.extras.execute_values(
            self.cur,
            "INSERT INTO users (id, created_at, email, password_hash, username) VALUES %s ON CONFLICT DO NOTHING",
            rows
        )

    def insert_posts(self, rows: list):
        import psycopg2.extras
        psycopg2.extras.execute_values(
            self.cur,
            "INSERT INTO posts (id, author_id, comment_count, content, created_at, image_url, like_count, updated_at) VALUES %s",
            rows
        )

    def insert_likes(self, rows: list):
        import psycopg2.extras
        psycopg2.extras.execute_values(
            self.cur,
            "INSERT INTO likes (id, created_at, post_id, user_id) VALUES %s ON CONFLICT DO NOTHING",
            rows
        )

    def insert_comments(self, rows: list):
        import psycopg2.extras
        psycopg2.extras.execute_values(
            self.cur,
            "INSERT INTO comments (id, author_id, content, created_at, post_id) VALUES %s",
            rows
        )

    def commit(self):
        self.con.commit()

    def rollback(self):
        self.con.rollback()

    def close(self):
        if self.con:
            self.con.close()


# ---------------------------------------------------------------------------
# Backend factory
# ---------------------------------------------------------------------------

def create_backend(config: dict) -> DatabaseBackend:
    backend_type = config.get("backend", DEFAULT_CONFIG["backend"]).lower()
    if backend_type == "postgres":
        return PostgreSQLBackend(
            host=config.get("pg_host", DEFAULT_CONFIG["pg_host"]),
            port=int(config.get("pg_port", DEFAULT_CONFIG["pg_port"])),
            dbname=config.get("pg_dbname", DEFAULT_CONFIG["pg_dbname"]),
            user=config.get("pg_user", DEFAULT_CONFIG["pg_user"]),
            password=config.get("pg_password", DEFAULT_CONFIG["pg_password"]),
        )
    return SQLiteBackend(config.get("db_path") or DEFAULT_CONFIG["db_path"])


# ---------------------------------------------------------------------------
# Main populate logic (backend-agnostic)
# ---------------------------------------------------------------------------

def populate(config):
    total_users = config.get("total_users", DEFAULT_CONFIG["total_users"])
    batch_size = config.get("batch_size", DEFAULT_CONFIG["batch_size"])
    min_posts = config.get("min_posts_per_user", DEFAULT_CONFIG["min_posts_per_user"])
    max_posts = config.get("max_posts_per_user", DEFAULT_CONFIG["max_posts_per_user"])
    min_likes = config.get("min_likes_per_post", DEFAULT_CONFIG["min_likes_per_post"])
    max_likes = config.get("max_likes_per_post", DEFAULT_CONFIG["max_likes_per_post"])
    min_comments = config.get("min_comments_per_post", DEFAULT_CONFIG["min_comments_per_post"])
    max_comments = config.get("max_comments_per_post", DEFAULT_CONFIG["max_comments_per_post"])
    min_words = config.get("min_words_per_post", DEFAULT_CONFIG["min_words_per_post"])
    max_words = config.get("max_words_per_post", DEFAULT_CONFIG["max_words_per_post"])
    days_back = config.get("days_back", DEFAULT_CONFIG["days_back"])
    seed = config.get("seed", DEFAULT_CONFIG["seed"])
    skip_cleanup = config.get("skip_cleanup", DEFAULT_CONFIG["skip_cleanup"])
    do_vacuum = config.get("vacuum", DEFAULT_CONFIG["vacuum"])
    dry_run = config.get("dry_run", DEFAULT_CONFIG["dry_run"])

    if seed is not None:
        random.seed(seed)
        faker.Faker.seed(seed)

    db = create_backend(config)
    print(f"Connecting to {db.label()}...")
    db.connect()

    db.ensure_tables_exist()

    # Optionally clean existing data (keeps alice and bob)
    if not skip_cleanup:
        print("Cleaning existing comments, likes, posts and users (preserving alice and bob)...")
        db.cleanup()
        if do_vacuum:
            print("Running VACUUM...")
            db.vacuum()

    # Pre-hash "password" using bcrypt
    print("Generating bcrypt password hash for 'password'...")
    hashed_password = bcrypt.hashpw(b"password", bcrypt.gensalt()).decode("utf-8")

    now = datetime.datetime.now(datetime.timezone.utc)
    now_ms = int(now.timestamp() * 1000)
    today = now.date()
    start_date = today - datetime.timedelta(days=days_back)

    # Ensure alice and bob exist
    existing_users = db.get_existing_seed_users()

    users_to_insert = []
    for name, email in [("alice", "alice@example.com"), ("bob", "bob@example.com")]:
        if name not in existing_users:
            uid = str(uuid.uuid4())
            users_to_insert.append((uid, now_ms, email, hashed_password, name))
            existing_users[name] = uid

    # Ensure charlie exists / update password
    charlie_row = db._get_user_by_username("charlie")
    if charlie_row:
        charlie_id = charlie_row[0]
        db.upsert_seed_user_password(charlie_id, hashed_password)
        existing_users["charlie"] = charlie_id
    else:
        charlie_id = str(uuid.uuid4())
        users_to_insert.append((charlie_id, now_ms, "charlie@example.com", hashed_password, "charlie"))
        existing_users["charlie"] = charlie_id

    fake = faker.Faker()

    current_user_count = len(existing_users)
    users_needed = max(0, total_users - current_user_count)
    print(f"Generating {users_needed} additional users (target total: {total_users})...")

    seen_usernames = set(existing_users.keys())
    all_user_ids = list(existing_users.values())

    for i in range(users_needed):
        uid = str(uuid.uuid4())
        all_user_ids.append(uid)
        base_username = fake.user_name().replace(".", "_").lower()
        username = base_username
        suffix = 1
        while username in seen_usernames:
            username = f"{base_username}_{suffix}"
            suffix += 1
        seen_usernames.add(username)

        email = f"{username}_{i}@example.com"
        created_at_dt = fake.date_between_dates(date_start=start_date, date_end=today)
        created_at_ms = int(
            datetime.datetime.combine(
                created_at_dt, fake.time_object(), tzinfo=datetime.timezone.utc
            ).timestamp() * 1000
        )
        users_to_insert.append((uid, created_at_ms, email, hashed_password, username))

    # Insert users
    if users_to_insert:
        print(f"Inserting {len(users_to_insert)} users into database...")
        db.insert_users(users_to_insert)
        if not dry_run:
            db.commit()
        print("Users inserted successfully.")

    # Generate posts, likes, comments
    print(f"Generating posts ({min_posts}-{max_posts} per user) for {len(all_user_ids)} users...")
    posts_batch = []
    likes_batch = []
    comments_batch = []

    total_posts = 0
    total_likes = 0
    total_comments = 0

    num_users = len(all_user_ids)

    photo_urls = [
        "https://picsum.photos/seed/{}/800/600.jpg",
        "https://thumbs.dreamstime.com/b/spring-green-blossom-139171768.jpg?w=768",
        "https://images.unsplash.com/photo-1790292366107-9f84204d06cb?q=80&w=627&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=NHwxMjA3fDA%3D",
        "https://images.unsplash.com/photo-1794241491075-7664ac84987e?q=80&w=1170&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=NHwxMjA3fDA%3D",
        "/media/photo_{}.jpg"
    ]
    video_urls = [
        "https://www.pexels.com/download/video/856949/",
        "https://www.pexels.com/download/video/10166812/",
        "https://www.pexels.com/download/video/6214216/",
        "/media/video_{}.webm",
        "/media/clip_{}.mov"
    ]

    for idx, author_id in enumerate(all_user_ids):
        post_count_for_user = random.randint(min_posts, max_posts)
        for _ in range(post_count_for_user):
            post_id = str(uuid.uuid4())
            post_date = fake.date_between_dates(date_start=start_date, date_end=today)
            post_time = fake.time_object()
            post_dt = datetime.datetime.combine(post_date, post_time, tzinfo=datetime.timezone.utc)
            post_created_ms = int(post_dt.timestamp() * 1000)

            word_count = random.randint(min_words, max_words)
            content = " ".join(fake.words(nb=word_count)).capitalize()

            is_video = random.choice([True, False])
            if is_video:
                img_url = random.choice(video_urls).format(post_id[:8])
            else:
                img_url = random.choice(photo_urls).format(post_id[:8])

            # Likes
            num_likes = random.randint(min_likes, max_likes)
            if num_likes > 0:
                liked_user_indices = random.sample(range(num_users), min(num_likes, num_users))
                for u_idx in liked_user_indices:
                    like_id = str(uuid.uuid4())
                    like_user_id = all_user_ids[u_idx]
                    like_created_ms = random.randint(post_created_ms, max(post_created_ms, now_ms))
                    likes_batch.append((like_id, like_created_ms, post_id, like_user_id))
                    total_likes += 1

            # Comments
            num_comments = random.randint(min_comments, max_comments)
            for _ in range(num_comments):
                comment_id = str(uuid.uuid4())
                comment_author_id = all_user_ids[random.randint(0, num_users - 1)]
                comment_content = " ".join(fake.words(nb=random.randint(2, 10))).capitalize()
                comment_created_ms = random.randint(post_created_ms, max(post_created_ms, now_ms))
                comments_batch.append((comment_id, comment_author_id, comment_content, comment_created_ms, post_id))
                total_comments += 1

            posts_batch.append((
                post_id,
                author_id,
                num_comments,
                content,
                post_created_ms,
                img_url,
                num_likes,
                post_created_ms
            ))
            total_posts += 1

        # Flush batches — posts MUST be inserted before likes/comments
        # to satisfy foreign key constraints. Flush all three together
        # whenever posts reaches the threshold.
        if len(posts_batch) >= batch_size:
            db.insert_posts(posts_batch)
            posts_batch.clear()
            if likes_batch:
                db.insert_likes(likes_batch)
                likes_batch.clear()
            if comments_batch:
                db.insert_comments(comments_batch)
                comments_batch.clear()

        if (idx + 1) % 1000 == 0 or idx + 1 == num_users:
            print(
                f"Processed {idx + 1}/{num_users} users "
                f"({total_posts} posts, {total_likes} likes, {total_comments} comments)..."
            )

    # Flush remaining batches
    if posts_batch:
        db.insert_posts(posts_batch)
    if likes_batch:
        db.insert_likes(likes_batch)
    if comments_batch:
        db.insert_comments(comments_batch)

    if not dry_run:
        db.commit()
        print("Bulk transaction committed successfully.")
    else:
        db.rollback()
        print("Dry run complete (rolled back).")

    db.close()

    print("\nDatabase population completed!")
    print(f"Backend:        {db.label()}")
    print(f"Total Users:    {len(all_user_ids)}")
    print(f"Total Posts:    {total_posts}")
    print(f"Total Likes:    {total_likes}")
    print(f"Total Comments: {total_comments}")


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Populate Instakill database (SQLite or PostgreSQL) with configurable test data via YAML configuration."
    )
    parser.add_argument(
        "config_file",
        nargs="?",
        default=None,
        help=f"Path to YAML configuration file (default: {DEFAULT_CONFIG_FILE})",
    )
    parser.add_argument(
        "--config",
        dest="config_flag",
        type=str,
        default=None,
        help="Alternative flag to specify the YAML configuration file",
    )
    # Backend selection
    parser.add_argument(
        "--backend",
        choices=["sqlite", "postgres"],
        default=None,
        help="Database backend: sqlite (default) or postgres",
    )
    # SQLite overrides
    parser.add_argument("--db", type=str, default=None, help="(SQLite) Override db_path")
    # PostgreSQL overrides
    parser.add_argument("--pg-host", type=str, default=None, help="(PostgreSQL) Override pg_host")
    parser.add_argument("--pg-port", type=int, default=None, help="(PostgreSQL) Override pg_port")
    parser.add_argument("--pg-dbname", type=str, default=None, help="(PostgreSQL) Override pg_dbname")
    parser.add_argument("--pg-user", type=str, default=None, help="(PostgreSQL) Override pg_user")
    parser.add_argument("--pg-password", type=str, default=None, help="(PostgreSQL) Override pg_password")
    # Population overrides
    parser.add_argument("--users", type=int, default=None, help="Override total_users")
    parser.add_argument("--batch-size", type=int, default=None, help="Override batch_size")
    parser.add_argument("--dry-run", action="store_true", help="Override dry_run to true")
    parser.add_argument("--skip-cleanup", action="store_true", help="Override skip_cleanup to true")

    args = parser.parse_args()

    cfg_file = args.config_flag or args.config_file or DEFAULT_CONFIG_FILE
    config = dict(DEFAULT_CONFIG)

    if os.path.exists(cfg_file):
        print(f"Loading configuration from YAML file: {os.path.abspath(cfg_file)}")
        file_cfg = load_yaml_config(cfg_file)
        config.update({k: v for k, v in file_cfg.items() if v is not None})
    else:
        print(f"Configuration file '{cfg_file}' not found. Using defaults.")

    # Apply CLI overrides
    if args.backend is not None:
        config["backend"] = args.backend
    if args.db is not None:
        config["db_path"] = args.db
    if args.pg_host is not None:
        config["pg_host"] = args.pg_host
    if args.pg_port is not None:
        config["pg_port"] = args.pg_port
    if args.pg_dbname is not None:
        config["pg_dbname"] = args.pg_dbname
    if args.pg_user is not None:
        config["pg_user"] = args.pg_user
    if args.pg_password is not None:
        config["pg_password"] = args.pg_password
    if args.users is not None:
        config["total_users"] = args.users
    if args.batch_size is not None:
        config["batch_size"] = args.batch_size
    if args.dry_run:
        config["dry_run"] = True
    if args.skip_cleanup:
        config["skip_cleanup"] = True

    populate(config)


if __name__ == "__main__":
    main()
