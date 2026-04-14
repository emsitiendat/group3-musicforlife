import os
import re
from flask import Flask
from flask_sqlalchemy import SQLAlchemy
from mutagen.mp3 import MP3


def create_app():
    app = Flask(__name__)
    app.config["SQLALCHEMY_DATABASE_URI"] = "sqlite:///music_app.db"
    app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False
    app.config["ALLOWED_IMAGE_EXTENSIONS"] = {"png", "jpg", "jpeg", "gif", "webp"}
    db = SQLAlchemy(app)
    return app, db


app, db = create_app()

from models import Song


def normalize_filename(filename):
    name, ext = os.path.splitext(filename)

    name = re.sub(r"[^\w\s-]", "", name, flags=re.IGNORECASE).lower()

    name = re.sub(r"\s+", " ", name).strip()
    return name


def upload_music(audio_dir, covers_dir, csv_file_path):
    with app.app_context():
        db.create_all()

        try:
            if not os.path.exists(audio_dir):
                print(f"Error: Audio directory '{audio_dir}' not found.")
                return

            for filename in os.listdir(audio_dir):
                if filename.endswith((".mp3", ".wav", ".ogg", ".flac")):
                    filepath = os.path.join(audio_dir, filename)

                    try:
                        audio = MP3(filepath)
                        duration = (
                            int(audio.info.length)
                            if audio and audio.info and hasattr(audio.info, "length")
                            else None
                        )
                        title = (
                            audio.get("TIT2", [filename])[0]
                            if audio.get("TIT2")
                            else filename.rsplit(".", 1)[0]
                        )
                        artist = audio.get("TPE1", [""])[0] if audio.get("TPE1") else ""
                        lyrics = ""

                        normalized_music_filename = normalize_filename(
                            filename.rsplit(".", 1)[0]
                        )

                        cover_art_path = None
                        if covers_dir and os.path.exists(covers_dir):
                            for cover_ext in app.config["ALLOWED_IMAGE_EXTENSIONS"]:
                                cover_filename = (
                                    normalize_filename(os.path.splitext(filename)[0])
                                    + "."
                                    + cover_ext
                                )
                                cover_path = os.path.join(covers_dir, cover_filename)
                                if (
                                    os.path.exists(cover_path)
                                    and normalize_filename(
                                        os.path.splitext(os.path.basename(cover_path))[
                                            0
                                        ]
                                    )
                                    == normalize_music_filename
                                ):
                                    cover_art_path = os.path.relpath(
                                        cover_path, "static"
                                    ).replace("\\", "/")
                                    break

                        new_song = Song(
                            title=title,
                            artist=artist,
                            file_path=os.path.relpath(filepath, "static").replace(
                                "\\", "/"
                            ),
                            cover_art_path=cover_art_path,
                            lyrics=lyrics,
                            duration=duration,
                        )

                        db.session.add(new_song)
                        print(f"Added song: {title} - {artist}")

                    except Exception as e:
                        print(f"Error processing '{filename}': {e}")
                        db.session.rollback()

            db.session.commit()
            print("Music upload completed!")

        except Exception as e:
            print(f"General error during upload: {e}")
            db.session.rollback()


if __name__ == "__main__":
    audio_dir = (
        "C:/Users/Kimi no Na wa/eclipse-workspace/Group_work/music_demo/static/audio"
    )
    covers_dir = "C:/Users/Kimi no Na wa/eclipse-workspace/Group_work/music_demo/static/img/covers"
    csv_file_path = "C:/Users/YourName/Music/songs.csv"
    upload_music(audio_dir, covers_dir, csv_file_path)
