import random
import mysql.connector

# ====== CONFIG ======
DB_CONFIG = {
    "host": "localhost",
    "user": "root",
    "password": "pass",
    "database": "db-name"
}

MOVIE_FILE = "movie_ids.txt" #9238
NUM_USERS = 1000
MOVIES_PER_USER = 10

# ====== READ MOVIE IDS ======
with open(MOVIE_FILE, "r") as f:
    movie_ids = [line.strip() for line in f if line.strip()]

print(f"Loaded {len(movie_ids)} movie IDs")

# ====== CONNECT TO DB ======
conn = mysql.connector.connect(**DB_CONFIG)
cursor = conn.cursor()

# ====== INSERT DATA ======
insert_query = """
INSERT INTO ratings (user_id, movie_id, rating)
VALUES (%s, %s, %s)
"""

batch = []

for user_id in range(1, NUM_USERS + 1):
    # pick 100 unique movies
    selected_movies = random.sample(movie_ids, MOVIES_PER_USER)

    for movie_id in selected_movies:
        rating = random.randint(1, 5)
        batch.append((str(user_id), movie_id, rating))

    # Insert in batches (performance boost)
    if user_id % 50 == 0:
        cursor.executemany(insert_query, batch)
        conn.commit()
        print(f"Inserted up to user {user_id}")
        batch = []

# insert remaining
if batch:
    cursor.executemany(insert_query, batch)
    conn.commit()

# ====== CLEANUP ======
cursor.close()
conn.close()

print("Done inserting data!")