#!/usr/bin/env python3
"""
populate_db.py - Instakill SQLite Database Populator
Fills the SQLite database with configurable test data according to Instakill's schema.
Reads configuration from a YAML file (defaults to populate_config.yaml) conforming to schema.config.json.
"""

import argparse
import datetime
import os
import random
import sqlite3
import uuid
import bcrypt
import faker

try:
    import yaml
except ImportError:
    yaml = None


DEFAULT_CONFIG_FILE = "populate_config.yaml"

DEFAULT_CONFIG = {
    "db_path": "instakill.db",
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


def ensure_tables_exist(cur):
    cur.execute("""
    CREATE TABLE IF NOT EXISTS users (
        id char(36) not null,
        created_at timestamp not null,
        email varchar(255) not null unique,
        password_hash varchar(255) not null,
        username varchar(255) not null unique,
        primary key (id)
    );
    """)
    cur.execute("""
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
    cur.execute("""
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
    cur.execute("""
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
    cur.execute("CREATE INDEX IF NOT EXISTS idx_posts_author ON posts (author_id);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts (created_at);")
    cur.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_likes_post_user ON likes (post_id, user_id);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_comments_post ON comments (post_id);")


def populate(config):
    db_path = config.get("db_path") or DEFAULT_CONFIG["db_path"]
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
    vacuum = config.get("vacuum", DEFAULT_CONFIG["vacuum"])
    dry_run = config.get("dry_run", DEFAULT_CONFIG["dry_run"])

    if seed is not None:
        random.seed(seed)
        faker.Faker.seed(seed)

    print(f"Target SQLite database: {os.path.abspath(db_path)}")
    db_dir = os.path.dirname(os.path.abspath(db_path))
    if db_dir:
        os.makedirs(db_dir, exist_ok=True)

    con = sqlite3.connect(db_path)
    cur = con.cursor()

    ensure_tables_exist(cur)

    # Clean existing data except alice and bob if cleanup requested
    if not skip_cleanup:
        print("Cleaning existing comments, likes, posts and users (preserving alice and bob)...")
        cur.execute("DELETE FROM comments")
        cur.execute("DELETE FROM likes")
        cur.execute("DELETE FROM posts")
        cur.execute("DELETE FROM users WHERE username NOT IN ('alice', 'bob')")
        con.commit()
        if vacuum:
            print("Running VACUUM...")
            cur.execute("VACUUM")
            con.commit()

    # Pre-hash "password" using bcrypt
    print("Generating bcrypt password hash for 'password'...")
    hashed_password = bcrypt.hashpw(b"password", bcrypt.gensalt()).decode("utf-8")

    now = datetime.datetime.now(datetime.timezone.utc)
    now_ms = int(now.timestamp() * 1000)
    today = now.date()
    start_date = today - datetime.timedelta(days=days_back)

    # Ensure alice and bob exist
    cur.execute("SELECT id, username FROM users WHERE username IN ('alice', 'bob')")
    existing_users = {row[1]: row[0] for row in cur.fetchall()}

    users_to_insert = []
    if "alice" not in existing_users:
        alice_id = str(uuid.uuid4())
        users_to_insert.append((alice_id, now_ms, "alice@example.com", hashed_password, "alice"))
        existing_users["alice"] = alice_id

    if "bob" not in existing_users:
        bob_id = str(uuid.uuid4())
        users_to_insert.append((bob_id, now_ms, "bob@example.com", hashed_password, "bob"))
        existing_users["bob"] = bob_id

    # Add user charlie with password set to 'password'
    cur.execute("SELECT id FROM users WHERE username = 'charlie'")
    charlie_row = cur.fetchone()
    if charlie_row:
        charlie_id = charlie_row[0]
        cur.execute("UPDATE users SET password_hash = ? WHERE id = ?", (hashed_password, charlie_id))
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

    # Build users list
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
        created_at_ms = int(datetime.datetime.combine(created_at_dt, fake.time_object(), tzinfo=datetime.timezone.utc).timestamp() * 1000)
        users_to_insert.append((uid, created_at_ms, email, hashed_password, username))

    # Fast bulk insert settings
    cur.execute("PRAGMA synchronous = OFF")
    cur.execute("PRAGMA journal_mode = MEMORY")

    # Insert users
    if users_to_insert:
        print(f"Inserting {len(users_to_insert)} users into database...")
        cur.executemany(
            "INSERT INTO users (id, created_at, email, password_hash, username) VALUES (?, ?, ?, ?, ?)",
            users_to_insert
        )
        if not dry_run:
            con.commit()
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

    # Random photo and video URLs
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
            # created_on in the past week using faker.date_between_dates()
            post_date = fake.date_between_dates(date_start=start_date, date_end=today)
            post_time = fake.time_object()
            post_dt = datetime.datetime.combine(post_date, post_time, tzinfo=datetime.timezone.utc)
            post_created_ms = int(post_dt.timestamp() * 1000)

            # Random content up to random words
            word_count = random.randint(min_words, max_words)
            content = " ".join(fake.words(nb=word_count)).capitalize()

            # Random media type (photo or video)
            is_video = random.choice([True, False])
            if is_video:
                img_url = random.choice(video_urls).format(post_id[:8])
            else:
                img_url = random.choice(photo_urls).format(post_id[:8])

            # Likes: configurable range (default 0-20)
            num_likes = random.randint(min_likes, max_likes)
            if num_likes > 0:
                liked_user_indices = random.sample(range(num_users), min(num_likes, num_users))
                for u_idx in liked_user_indices:
                    like_id = str(uuid.uuid4())
                    like_user_id = all_user_ids[u_idx]
                    like_created_ms = random.randint(post_created_ms, max(post_created_ms, now_ms))
                    likes_batch.append((like_id, like_created_ms, post_id, like_user_id))
                    total_likes += 1

            # Comments: configurable range (default 0-10)
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

        # Batch insert to keep memory and performance optimal
        if len(posts_batch) >= batch_size:
            cur.executemany(
                "INSERT INTO posts (id, author_id, comment_count, content, created_at, image_url, like_count, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                posts_batch
            )
            posts_batch.clear()

        if len(likes_batch) >= batch_size:
            cur.executemany(
                "INSERT INTO likes (id, created_at, post_id, user_id) VALUES (?, ?, ?, ?)",
                likes_batch
            )
            likes_batch.clear()

        if len(comments_batch) >= batch_size:
            cur.executemany(
                "INSERT INTO comments (id, author_id, content, created_at, post_id) VALUES (?, ?, ?, ?, ?)",
                comments_batch
            )
            comments_batch.clear()

        if (idx + 1) % 1000 == 0 or idx + 1 == num_users:
            print(f"Processed {idx + 1}/{num_users} users ({total_posts} posts, {total_likes} likes, {total_comments} comments)...")

    # Flush remaining batches
    if posts_batch:
        cur.executemany(
            "INSERT INTO posts (id, author_id, comment_count, content, created_at, image_url, like_count, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            posts_batch
        )
        posts_batch.clear()

    if likes_batch:
        cur.executemany(
            "INSERT INTO likes (id, created_at, post_id, user_id) VALUES (?, ?, ?, ?)",
            likes_batch
        )
        likes_batch.clear()

    if comments_batch:
        cur.executemany(
            "INSERT INTO comments (id, author_id, content, created_at, post_id) VALUES (?, ?, ?, ?, ?)",
            comments_batch
        )
        comments_batch.clear()

    if not dry_run:
        con.commit()
        print("Bulk transaction committed successfully.")
    else:
        con.rollback()
        print("Dry run complete (rolled back).")

    cur.execute("PRAGMA synchronous = NORMAL")
    con.close()

    print("\nDatabase population completed!")
    print(f"Database File: {os.path.abspath(db_path)}")
    print(f"Total Users: {len(all_user_ids)}")
    print(f"Total Posts: {total_posts}")
    print(f"Total Likes: {total_likes}")
    print(f"Total Comments: {total_comments}")


def main():
    parser = argparse.ArgumentParser(
        description="Populate Instakill SQLite Database with configurable test data via YAML configuration."
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
    # CLI overrides
    parser.add_argument("--db", type=str, default=None, help="Override db_path")
    parser.add_argument("--users", type=int, default=None, help="Override total_users")
    parser.add_argument("--batch-size", type=int, default=None, help="Override batch_size")
    parser.add_argument("--dry-run", action="store_true", help="Override dry_run to true")
    parser.add_argument("--skip-cleanup", action="store_true", help="Override skip_cleanup to true")

    args = parser.parse_args()

    # Determine config file to load
    cfg_file = args.config_flag or args.config_file or DEFAULT_CONFIG_FILE
    config = dict(DEFAULT_CONFIG)

    if os.path.exists(cfg_file):
        print(f"Loading configuration from YAML file: {os.path.abspath(cfg_file)}")
        file_cfg = load_yaml_config(cfg_file)
        config.update({k: v for k, v in file_cfg.items() if v is not None})
    else:
        print(f"Configuration file '{cfg_file}' not found. Using defaults (schema: schema.config.json).")

    # Apply CLI overrides if explicitly given
    if args.db is not None:
        config["db_path"] = args.db
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
