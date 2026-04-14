
import sys
import os


project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
if project_root not in sys.path:
    sys.path.insert(0, project_root)

try:
    from app import app, db
    from models import Song, normalize_text
except ImportError as e:
    print(f"Import Error: {e}")
    print("Hãy đảm bảo bạn đang chạy script từ thư mục đúng hoặc đã cấu hình PYTHONPATH.")
    sys.exit(1)


def run_population():
    with app.app_context():
        print("Populating normalized fields for existing songs...")
        songs_to_update = Song.query.all()
        updated_count = 0
        if not songs_to_update:
            print("No existing songs found.")
            return

        for song in songs_to_update:
            needs_update = False
            current_title_norm = getattr(song, 'title_normalized', None)
            current_artist_norm = getattr(song, 'artist_normalized', None)

            new_title_norm = normalize_text(song.title)
            new_artist_norm = normalize_text(song.artist)

            if current_title_norm != new_title_norm:
                song.title_normalized = new_title_norm
                needs_update = True


            if current_artist_norm != new_artist_norm:
                song.artist_normalized = new_artist_norm
                needs_update = True


            if needs_update:
                updated_count += 1


        if updated_count > 0:
            try:
                print(f"Attempting to commit updates for {updated_count} songs...")
                db.session.commit()
                print(f"Successfully updated normalized fields for {updated_count} songs.")
            except Exception as e:
                db.session.rollback()
                print(f"ERROR committing updates: {e}")
                traceback.print_exc()
        else:
            print("No existing songs needed normalization update.")

if __name__ == "__main__":
    import traceback
    run_population()