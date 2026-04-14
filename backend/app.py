import os
import random
import click
from flask import (
    Flask,
    render_template,
    redirect,
    url_for,
    flash,
    request,
    abort,
    jsonify,
    current_app,
)
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate
from flask_login import (
    LoginManager,
    UserMixin,
    login_user,
    logout_user,
    login_required,
    current_user,
)
from flask_wtf import FlaskForm
from wtforms import (
    StringField,
    PasswordField,
    SubmitField,
    TextAreaField,
    IntegerField,
    SelectField,
    BooleanField,
)
from wtforms_sqlalchemy.fields import QuerySelectField, QuerySelectMultipleField
from wtforms.validators import (
    DataRequired,
    Length,
    EqualTo,
    ValidationError,
    InputRequired,
    Optional,
    NumberRange,
)
from flask_admin import Admin, AdminIndexView, expose
from flask_admin.contrib.sqla import ModelView
from flask_admin.form.upload import FileUploadField
from markupsafe import Markup
from html import escape
from flask_wtf.file import FileField, FileAllowed

from werkzeug.utils import secure_filename
from werkzeug.security import generate_password_hash, check_password_hash
from flask_babel import Babel
from datetime import datetime, timedelta

from models import (
    db, 
    User, 
    Song, 
    Playlist, 
    ListeningHistory, 
    Comment, 
    Artist, 
    Story,            
    likes, 
    playlist_songs, 
    followers         
)
from sqlalchemy import func, desc, asc, or_, distinct, text
from unidecode import unidecode
from datetime import datetime
from urllib.parse import unquote
import mutagen
import traceback


def normalize_text(text):
    """Chuyển thành chữ thường và loại bỏ dấu."""
    if not text:
        return ""
    try:
        return unidecode(text).lower()
    except Exception:
        return text.lower()


def allowed_file(filename, allowed_extensions):
    """Kiểm tra xem tên file có đuôi mở rộng hợp lệ không."""
    return "." in filename and filename.rsplit(".", 1)[1].lower() in allowed_extensions


app = Flask(__name__)

app.config["SECRET_KEY"] = "your_very_secret_key_here_please_change_me"
basedir = os.path.abspath(os.path.dirname(__file__))
db_path = os.path.join(basedir, "instance", "music_app.db")
app.config["SQLALCHEMY_DATABASE_URI"] = "sqlite:///" + db_path
app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False
app.config["FLASK_ADMIN_SWATCH"] = "cerulean"
app.config["ALLOWED_AUDIO_EXTENSIONS"] = {"mp3", "wav", "ogg", "m4a", "flac"}
app.config["ALLOWED_IMAGE_EXTENSIONS"] = {"png", "jpg", "jpeg", "gif", "webp"}
app.config["BABEL_DEFAULT_LOCALE"] = "vi"
app.config["WTF_CSRF_ENABLED"] = False


static_folder_path = os.path.join(basedir, "static")
app.config["STATIC_FOLDER"] = static_folder_path

AUDIO_FOLDER = os.path.join(static_folder_path, "audio")
COVER_FOLDER = os.path.join(static_folder_path, "img", "covers")
AVATAR_FOLDER_NAME = "avatars"
AVATAR_FOLDER_PATH = os.path.join(static_folder_path, "img", AVATAR_FOLDER_NAME)


os.makedirs(AUDIO_FOLDER, exist_ok=True)
os.makedirs(COVER_FOLDER, exist_ok=True)
os.makedirs(AVATAR_FOLDER_PATH, exist_ok=True)
PLAYLIST_COVER_FOLDER_NAME = "playlist_covers"
PLAYLIST_COVER_FOLDER_PATH = os.path.join(
    static_folder_path, "img", PLAYLIST_COVER_FOLDER_NAME
)

os.makedirs(PLAYLIST_COVER_FOLDER_PATH, exist_ok=True)
instance_folder_path = os.path.join(basedir, "instance")
os.makedirs(instance_folder_path, exist_ok=True)


import time 

def save_playlist_cover(file_data, playlist):
    if not file_data or not file_data.filename:
        return None

    if not allowed_file(file_data.filename, app.config["ALLOWED_IMAGE_EXTENSIONS"]):
        flash("Loại file ảnh bìa playlist không hợp lệ!", "warning")
        return None

    try:
        filename_ext = file_data.filename.rsplit(".", 1)[1].lower()
        timestamp = int(time.time())
        
        filename = secure_filename(
            f"playlist_{playlist.id}_user_{playlist.user_id}_{timestamp}.{filename_ext}"
        )
        save_path = os.path.join(PLAYLIST_COVER_FOLDER_PATH, filename)

        file_data.save(save_path)
        print(f"Saved new playlist cover to: {save_path}")

        relative_path = os.path.join(
            "img", PLAYLIST_COVER_FOLDER_NAME, filename
        ).replace("\\", "/")
        return relative_path
    except Exception as e:
        print(f"Error saving playlist cover: {e}")
        traceback.print_exc()
        flash("Lỗi khi lưu ảnh bìa playlist.", "danger")
        return None

def delete_file(relative_path):
    """Xóa file dựa trên đường dẫn tương đối từ thư mục static."""
    if not relative_path:
        return
    try:
        full_path = os.path.join(static_folder_path, relative_path)
        if os.path.exists(full_path):
            os.remove(full_path)
            print(f"Deleted file: {full_path}")

    except Exception as e:
        print(f"Error deleting file {relative_path}: {e}")


db.init_app(app)
migrate = Migrate(app, db)
login_manager = LoginManager()
login_manager.init_app(app)
login_manager.login_view = "login"
login_manager.login_message = "Bạn cần đăng nhập để thực hiện chức năng này."
login_manager.login_message_category = "info"
babel = Babel()
babel.init_app(app)


def user_query_factory():
    return User.query.order_by(User.username)


def song_query_factory():
    return Song.query.order_by(Song.artist, Song.title)


@login_manager.user_loader
def load_user(user_id):
    try:
        return db.session.get(User, int(user_id))
    except (TypeError, ValueError):
        return None


class RegistrationForm(FlaskForm):
    username = StringField(
        "Tên đăng nhập", validators=[DataRequired(), Length(min=4, max=25)]
    )
    password = PasswordField("Mật khẩu", validators=[DataRequired(), Length(min=6)])
    confirm_password = PasswordField(
        "Xác nhận mật khẩu",
        validators=[
            DataRequired(),
            EqualTo("password", message="Mật khẩu xác nhận không khớp."),
        ],
    )
    submit = SubmitField("Đăng ký")

    def validate_username(self, username):
        user = User.query.filter_by(username=username.data).first()
        if user:
            raise ValidationError(
                "Tên đăng nhập này đã được sử dụng. Vui lòng chọn tên khác."
            )


class LoginForm(FlaskForm):
    username = StringField("Tên đăng nhập", validators=[DataRequired()])
    password = PasswordField("Mật khẩu", validators=[DataRequired()])
    submit = SubmitField("Đăng nhập")


class CreatePlaylistForm(FlaskForm):
    name = StringField(
        "Tên Playlist",
        validators=[
            InputRequired(message="Tên playlist không được để trống."),
            Length(min=1, max=100),
        ],
    )
    is_public = BooleanField("Công khai Playlist", default=True)
    cover_image = FileField(
        "Ảnh bìa tùy chỉnh (tùy chọn)",
        validators=[
            Optional(),
            FileAllowed(
                app.config["ALLOWED_IMAGE_EXTENSIONS"], "Chỉ chấp nhận file ảnh!"
            ),
        ],
    )
    remove_cover = BooleanField("Xóa ảnh bìa hiện tại và dùng ảnh tự động")
    submit = SubmitField("Lưu Playlist")


class UserAdminForm(FlaskForm):
    username = StringField(
        "Tên đăng nhập", validators=[DataRequired(), Length(min=4, max=25)]
    )
    role = SelectField(
        "Vai trò",
        choices=[("user", "User"), ("admin", "Admin")],
        validators=[DataRequired(message="Vui lòng chọn vai trò.")],
        default="user",
    )
    password = PasswordField(
        "Mật khẩu mới (để trống nếu không đổi)",
        validators=[
            Optional(),
            Length(min=6, message="Mật khẩu phải có ít nhất 6 ký tự."),
        ],
    )
    confirm_password = PasswordField(
        "Xác nhận mật khẩu mới",
        validators=[EqualTo("password", message="Mật khẩu xác nhận không khớp.")],
    )
    submit = SubmitField("Lưu")


class SongAdminForm(FlaskForm):
    title = StringField("Tiêu đề", validators=[DataRequired(), Length(max=100)])
    artist = StringField("Nghệ sĩ", validators=[DataRequired(), Length(max=100)])
    song_file = FileField(
        "File nhạc (chọn khi tạo mới/thay đổi)", validators=[Optional()]
    )
    cover_art_file = FileField(
        "Ảnh bìa (chọn khi tạo mới/thay đổi)",
        validators=[
            Optional(),
            FileAllowed(
                app.config["ALLOWED_IMAGE_EXTENSIONS"], "Chỉ cho phép file ảnh!"
            ),
        ],
    )
    lyrics = TextAreaField("Lời bài hát (LRC hoặc Text)", validators=[Optional()])

    duration = IntegerField(
        "Thời lượng (giây) - Tự động điền", validators=[Optional(), NumberRange(min=0)]
    )


class PlaylistAdminForm(FlaskForm):
    name = StringField("Tên Playlist", validators=[DataRequired(), Length(max=100)])
    owner = QuerySelectField(
        label="Chủ sở hữu (User)",
        query_factory=user_query_factory,
        get_label="username",
        allow_blank=False,
        validators=[DataRequired(message="Vui lòng chọn người dùng làm chủ sở hữu.")],
    )
    is_public = BooleanField("Công khai Playlist", default=True)
    is_featured = BooleanField("Nổi bật trên trang chủ", default=False)

    songs = QuerySelectMultipleField(
        label="Bài hát trong Playlist",
        query_factory=song_query_factory,
        get_label="title",
        validators=[Optional()],
    )
    submit = SubmitField("Lưu")


class ChangePasswordForm(FlaskForm):
    current_password = PasswordField("Mật khẩu hiện tại", validators=[DataRequired()])
    new_password = PasswordField(
        "Mật khẩu mới",
        validators=[
            DataRequired(),
            Length(min=6, message="Mật khẩu mới phải có ít nhất 6 ký tự."),
        ],
    )
    confirm_new_password = PasswordField(
        "Xác nhận mật khẩu mới",
        validators=[
            DataRequired(),
            EqualTo("new_password", message="Mật khẩu xác nhận không khớp."),
        ],
    )
    submit = SubmitField("Đổi mật khẩu")


class ProfileEditForm(FlaskForm):
    display_name = StringField("Tên hiển thị", validators=[Optional(), Length(max=100)])
    bio = TextAreaField("Giới thiệu bản thân", validators=[Optional(), Length(max=500)])
    profile_visibility = SelectField(
        "Chế độ hiển thị Hồ sơ",
        choices=[("public", "Công khai"), ("private", "Riêng tư")],
        validators=[DataRequired()],
    )
    avatar = FileField(
        "Ảnh đại diện mới (tùy chọn)",
        validators=[
            Optional(),
            FileAllowed(
                app.config["ALLOWED_IMAGE_EXTENSIONS"], "Chỉ chấp nhận file ảnh!"
            ),
        ],
    )
    submit = SubmitField("Lưu thay đổi")


class SecureAdminView(ModelView):
    def is_accessible(self):
        return current_user.is_authenticated and current_user.is_admin()

    def inaccessible_callback(self, name, **kwargs):
        flash("Bạn cần có quyền Admin để truy cập trang này.", "danger")
        return redirect(url_for("login", next=request.url))


class MyAdminIndexView(AdminIndexView):
    @expose("/")
    def index(self):

        if not current_user.is_authenticated or not current_user.is_admin():
            flash("Bạn cần có quyền Admin để truy cập trang này.", "danger")
            return redirect(url_for("login", next=request.url))

        try:

            user_count = db.session.query(func.count(User.id)).scalar()
            song_count = db.session.query(func.count(Song.id)).scalar()
            playlist_count = db.session.query(func.count(Playlist.id)).scalar()
            listen_count = db.session.query(func.count(ListeningHistory.id)).scalar()

            recent_songs = Song.query.order_by(desc(Song.uploaded_at)).limit(5).all()
            recent_users = User.query.order_by(desc(User.created_at)).limit(5).all()

            stats = {
                "user_count": user_count if user_count is not None else 0,
                "song_count": song_count if song_count is not None else 0,
                "playlist_count": playlist_count if playlist_count is not None else 0,
                "listen_count": listen_count if listen_count is not None else 0,
            }
        except Exception as e:

            print(f"Lỗi khi truy vấn dữ liệu dashboard: {e}")
            flash("Lỗi khi tải dữ liệu dashboard.", "danger")

            stats = {
                "user_count": "Lỗi",
                "song_count": "Lỗi",
                "playlist_count": "Lỗi",
                "listen_count": "Lỗi",
            }
            recent_songs = []
            recent_users = []

        return self.render(
            "admin/dashboard_index.html",
            stats=stats,
            recent_songs=recent_songs,
            recent_users=recent_users,
        )


class UserAdminView(SecureAdminView):

    form = UserAdminForm

    column_list = (
        "id",
        "username",
        "role",
        "followers_count",
        "following_count",
        "password_hash",
    )

    column_labels = {
        "id": "ID",
        "username": "Tên đăng nhập",
        "role": "Vai trò",
        "followers_count": "Người theo dõi",
        "following_count": "Đang theo dõi",
        "password_hash": "Password Hash (BẢO MẬT!)",
    }

    column_searchable_list = ("username", "role")
    column_filters = ("role",)
    can_create = True
    can_edit = True
    can_delete = True

    def on_model_change(self, form, model, is_created):
        """
        Được gọi khi một model (User) được tạo hoặc cập nhật từ form admin.
        Xử lý việc hash mật khẩu nếu admin đã nhập mật khẩu mới.
        """

        if form.password.data:
            model.set_password(form.password.data)
            flash("Đã cập nhật mật khẩu cho người dùng.", "success")
        elif is_created and not form.password.data:

            raise ValidationError(
                "Mật khẩu là bắt buộc khi tạo người dùng mới từ trang Admin."
            )


class ArtistAdminForm(FlaskForm):
    name = StringField("Tên Nghệ sĩ", validators=[DataRequired(), Length(max=150)])
    bio = TextAreaField("Tiểu sử", validators=[Optional()])

    profile_image_path_upload = FileField(
        "Ảnh Đại Diện Mới (để trống nếu không đổi)",
        validators=[
            Optional(),
            FileAllowed(
                app.config.get(
                    "ALLOWED_IMAGE_EXTENSIONS", {"png", "jpg", "jpeg", "gif", "webp"}
                ),
                "Chỉ cho phép file ảnh!",
            ),
        ],
    )
    banner_image_path_upload = FileField(
        "Ảnh Bìa Lớn Mới (Banner - để trống nếu không đổi)",
        validators=[
            Optional(),
            FileAllowed(
                app.config.get(
                    "ALLOWED_IMAGE_EXTENSIONS", {"png", "jpg", "jpeg", "gif", "webp"}
                ),
                "Chỉ cho phép file ảnh!",
            ),
        ],
    )
    submit = SubmitField("Lưu Nghệ sĩ")

    def __init__(
        self, formdata=None, obj=None, prefix="", data=None, meta=None, **kwargs
    ):
        """
        Lưu trữ đối tượng `obj` (model instance) để validator có thể truy cập.
        Flask-Admin sẽ truyền `obj` khi edit.
        """
        super(ArtistAdminForm, self).__init__(
            formdata=formdata, obj=obj, prefix=prefix, data=data, meta=meta, **kwargs
        )
        self._original_obj = obj

    def validate_name(self, name_field):

        original_name = None
        if self._original_obj and hasattr(self._original_obj, "name"):
            original_name = self._original_obj.name

        if original_name and original_name == name_field.data:
            return

        normalized_new_name = normalize_text(name_field.data)
        query = Artist.query.filter(
            func.lower(Artist.name_normalized) == func.lower(normalized_new_name)
        )

        if (
            self._original_obj
            and hasattr(self._original_obj, "id")
            and self._original_obj.id is not None
        ):
            query = query.filter(Artist.id != self._original_obj.id)

        existing_artist = query.first()

        if existing_artist:
            raise ValidationError("Tên nghệ sĩ này đã tồn tại. Vui lòng chọn tên khác.")


class ArtistAdminView(SecureAdminView):
    form = ArtistAdminForm

    column_list = ("name", "bio", "profile_image_path", "banner_image_path")
    column_labels = {
        "name": "Tên Nghệ sĩ",
        "bio": "Tiểu sử",
        "profile_image_path": "Ảnh Đại diện",
        "banner_image_path": "Ảnh Bìa",
    }

    form_excluded_columns = ("name_normalized", "songs")

    artist_profile_image_base_path = os.path.join(
        basedir, "static", "img", "artists", "profiles"
    )
    artist_banner_image_base_path = os.path.join(
        basedir, "static", "img", "artists", "banners"
    )
    os.makedirs(artist_profile_image_base_path, exist_ok=True)
    os.makedirs(artist_banner_image_base_path, exist_ok=True)
    artist_profile_image_relative_path = "img/artists/profiles/"
    artist_banner_image_relative_path = "img/artists/banners/"

    column_searchable_list = ("name", "name_normalized", "bio")
    column_filters = ("name",)

    @staticmethod
    def _bio_formatter(_view, _context, model, _name):
        if model.bio and len(model.bio) > 50:
            return model.bio[:50] + "..."
        return model.bio

    @staticmethod
    def _image_formatter(_view, _context, model, name):
        image_path = getattr(model, name, None)
        if image_path:
            return Markup(
                f'<img src="{url_for("static", filename=image_path)}" width="50" alt="Preview">'
            )
        return ""

    column_formatters = {
        "bio": _bio_formatter,
        "profile_image_path": _image_formatter,
        "banner_image_path": _image_formatter,
    }

    def _delete_file_if_exists(self, file_path_to_delete, base_storage_path):
        if file_path_to_delete:
            try:
                filename_only = os.path.basename(file_path_to_delete)
                full_path = os.path.join(base_storage_path, filename_only)
                if os.path.exists(full_path):
                    os.remove(full_path)
                    current_app.logger.info(f"Đã xóa file cũ: {full_path}")
                    return True
            except Exception as e:
                current_app.logger.error(
                    f"Không thể xóa file cũ {file_path_to_delete}: {e}"
                )

        return False

    def on_model_change(self, form, model, is_created):

        profile_image_file_data = form.profile_image_path_upload.data
        if profile_image_file_data and profile_image_file_data.filename:
            if is_created and not model.id:
                try:
                    db.session.flush([model])
                except Exception as e:
                    current_app.logger.error(
                        f"Lỗi khi flush model Artist để lấy ID: {e}"
                    )
                    raise ValidationError("Không thể lấy ID cho nghệ sĩ mới.")

            actual_saved_profile_filename = secure_filename(
                f"artist_profile_{model.id if model.id else 'temp'}_{profile_image_file_data.filename}"
            )

            save_profile_path = os.path.join(
                self.artist_profile_image_base_path, actual_saved_profile_filename
            )
            try:
                profile_image_file_data.save(save_profile_path)
            except Exception as e:
                flash(f"Lỗi khi lưu ảnh đại diện: {e}", "danger")
                current_app.logger.error(f"Lỗi khi lưu ảnh đại diện: {e}")

                return

            if (
                model.profile_image_path
                and actual_saved_profile_filename
                != os.path.basename(model.profile_image_path)
            ):
                self._delete_file_if_exists(
                    model.profile_image_path, self.artist_profile_image_base_path
                )

            model.profile_image_path = os.path.join(
                self.artist_profile_image_relative_path, actual_saved_profile_filename
            ).replace("\\", "/")

        banner_image_file_data = form.banner_image_path_upload.data
        if banner_image_file_data and banner_image_file_data.filename:
            if is_created and not model.id:
                try:
                    db.session.flush([model])
                except Exception as e:
                    current_app.logger.error(
                        f"Lỗi khi flush model Artist (banner): {e}"
                    )
                    raise ValidationError("Không thể lấy ID cho nghệ sĩ mới (banner).")

            actual_saved_banner_filename = secure_filename(
                f"artist_banner_{model.id if model.id else 'temp'}_{banner_image_file_data.filename}"
            )
            save_banner_path = os.path.join(
                self.artist_banner_image_base_path, actual_saved_banner_filename
            )
            try:
                banner_image_file_data.save(save_banner_path)
            except Exception as e:
                flash(f"Lỗi khi lưu ảnh bìa lớn: {e}", "danger")
                current_app.logger.error(f"Lỗi khi lưu ảnh bìa lớn: {e}")
                return

            if (
                model.banner_image_path
                and actual_saved_banner_filename
                != os.path.basename(model.banner_image_path)
            ):
                self._delete_file_if_exists(
                    model.banner_image_path, self.artist_banner_image_base_path
                )
            model.banner_image_path = os.path.join(
                self.artist_banner_image_relative_path, actual_saved_banner_filename
            ).replace("\\", "/")

        super(ArtistAdminView, self).on_model_change(form, model, is_created)


class SongAdminView(SecureAdminView):
    form = SongAdminForm

    column_list = (
        "id",
        "title",
        "artist",
        "duration",
        "like_count",
        "file_path",
        "cover_art_path",
        "lyrics",
        "share_count",
    )
    column_labels = {
        "id": "ID",
        "title": "Tiêu đề",
        "artist": "Nghệ sĩ",
        "duration": "Thời lượng",
        "like_count": "Lượt thích",
        "file_path": "File nhạc",
        "cover_art_path": "Ảnh bìa",
        "lyrics": "Lời bài hát",
        "share_count": "Lượt chia sẻ",
    }
    column_searchable_list = ("title", "artist")
    column_formatters = {
        "lyrics": lambda v, c, m, p: (
            (m.lyrics[:30] + "...") if m.lyrics and len(m.lyrics) > 30 else m.lyrics
        ),
        "duration": lambda v, c, m, p: (
            f"{m.duration // 60}:{m.duration % 60:02d}"
            if m.duration is not None
            else "N/A"
        ),
    }

    form_excluded_columns = (
        "playlists",
        "listens",
        "comments",
        "liked_by_users",
        "like_count",
        "share_count",
        "song_file",
        "cover_art_file",
    )
    form_widget_args = {"duration": {"readonly": True}}
    can_create = True
    can_edit = True
    can_delete = True

    def _allowed_file(self, filename, allowed_extensions):
        """Kiểm tra đuôi file có hợp lệ không."""
        if not isinstance(allowed_extensions, (set, tuple, list)):
            print(f"[ADMIN_ERROR] Lỗi cấu hình ALLOWED_EXTENSIONS")

            return False
        return (
            "." in filename and filename.rsplit(".", 1)[1].lower() in allowed_extensions
        )

    def _handle_file_upload(
        self, file_data, target_folder, allowed_extensions, current_path=None
    ):
        """Xử lý upload, kiểm tra extension, lưu file và trả về relative path."""
        if file_data and file_data.filename:

            if not self._allowed_file(file_data.filename, allowed_extensions):
                flash(
                    f'Loại file không hợp lệ cho "{file_data.filename}". Chỉ chấp nhận: {", ".join(allowed_extensions)}',
                    "danger",
                )
                return None

            filename = secure_filename(file_data.filename)
            save_path = os.path.join(target_folder, filename)
            try:
                file_data.save(save_path)
                relative_path = os.path.relpath(save_path, static_folder_path).replace(
                    "\\", "/"
                )

                if current_path and current_path != relative_path:
                    try:
                        old_file_full_path = os.path.join(
                            static_folder_path, current_path
                        )
                        if os.path.exists(old_file_full_path):
                            os.remove(old_file_full_path)
                            print(f"Deleted old file: {old_file_full_path}")
                    except Exception as e:
                        print(f"Warning: Could not delete old file {current_path}: {e}")
                return relative_path
            except Exception as e:
                print(f"Error saving file {filename} to {target_folder}: {e}")
                flash(f"Lỗi khi lưu file {filename}: {e}", "danger")
                return None

        return current_path

    def on_model_change(self, form, model, is_created):
        """Xử lý upload file và trích xuất metadata khi lưu model."""

        song_file_data = request.files.get(form.song_file.name)

        final_song_rel_path = model.file_path if not is_created else None

        if song_file_data and song_file_data.filename:
            print(f"Processing uploaded song file: {song_file_data.filename}")
            old_song_path = model.file_path if not is_created else None
            relative_song_path = self._handle_file_upload(
                song_file_data,
                AUDIO_FOLDER,
                app.config["ALLOWED_AUDIO_EXTENSIONS"],
                old_song_path,
            )
            if relative_song_path is None:
                if is_created:

                    raise ValidationError(
                        "Upload file nhạc thất bại hoặc file không hợp lệ. Không thể tạo bài hát."
                    )
                else:

                    flash("Upload file nhạc mới thất bại, giữ lại file cũ.", "warning")

            else:
                model.file_path = relative_song_path
                final_song_rel_path = relative_song_path
                print(f"Song file path updated to: {model.file_path}")
        elif is_created:

            raise ValidationError("File nhạc là bắt buộc khi tạo bài hát mới.")

        model.duration = None
        form.duration.data = None
        if final_song_rel_path:
            full_song_path = os.path.join(static_folder_path, final_song_rel_path)
            print(f"Attempting to read metadata from: {full_song_path}")
            try:
                if os.path.exists(full_song_path):
                    audio = mutagen.File(full_song_path, easy=True)
                    if audio is None:
                        audio = mutagen.File(full_song_path)

                    if audio and audio.info and hasattr(audio.info, "length"):
                        duration_seconds = audio.info.length
                        model.duration = round(duration_seconds)
                        form.duration.data = model.duration
                        print(f"Extracted duration: {model.duration} seconds")
                    else:
                        print(
                            f"Could not read duration from audio info for: {final_song_rel_path}"
                        )
                        flash(
                            f"Không thể đọc thông tin thời lượng từ file: {os.path.basename(final_song_rel_path)}.",
                            "warning",
                        )
                else:
                    print(f"File not found for metadata extraction: {full_song_path}")
                    flash(
                        f'Không tìm thấy file "{os.path.basename(final_song_rel_path)}" để đọc thời lượng.',
                        "warning",
                    )
            except Exception as e:
                print(
                    f"Error reading metadata with mutagen for {final_song_rel_path}: {e}"
                )
                flash(f"Lỗi khi đọc metadata từ file nhạc: {e}", "warning")

        cover_file_data = request.files.get(form.cover_art_file.name)
        if cover_file_data and cover_file_data.filename:
            print(f"Processing uploaded cover file: {cover_file_data.filename}")
            old_cover_path = model.cover_art_path if not is_created else None
            relative_cover_path = self._handle_file_upload(
                cover_file_data,
                COVER_FOLDER,
                app.config["ALLOWED_IMAGE_EXTENSIONS"],
                old_cover_path,
            )
            if relative_cover_path is None:
                flash(
                    "Upload ảnh bìa thất bại hoặc file không hợp lệ, ảnh bìa không được cập nhật.",
                    "warning",
                )
            else:
                model.cover_art_path = relative_cover_path
                print(f"Cover art path updated to: {model.cover_art_path}")


class PlaylistAdminView(SecureAdminView):
    form = PlaylistAdminForm

    @staticmethod
    def _get_song_count(model):
        """Đếm số bài hát, trả về int hoặc None nếu lỗi."""
        print(
            f"[ADMIN_DEBUG] _get_song_count called for model: {model} (ID: {getattr(model, 'id', 'N/A')})"
        )
        try:
            if not model or not hasattr(model, "id") or not model.id:
                print(f"[ADMIN_WARN] Invalid model or model.id in _get_song_count.")
                return 0

            if "playlist_songs" not in globals():
                print(
                    "[ADMIN_ERROR] 'playlist_songs' table object is somehow not available!"
                )
                return None

            playlist_id = model.id
            print(f"[ADMIN_DEBUG] Querying song count for playlist_id: {playlist_id}")

            count = (
                db.session.query(func.count(playlist_songs.c.song_id))
                .filter(playlist_songs.c.playlist_id == playlist_id)
                .scalar()
            )
            print(
                f"[ADMIN_DEBUG] Raw count result from scalar() for playlist {playlist_id}: {count}"
            )
            return count if count is not None else 0
        except Exception as e:
            print(
                f"[ADMIN_ERROR] Exception in _get_song_count for playlist ID {getattr(model, 'id', 'N/A')}: {e.__class__.__name__}: {e}"
            )
            traceback.print_exc()
            return None

    @staticmethod
    def _songs_formatter(view, context, model, name):
        """Định dạng xem trước bài hát."""
        if not model or not hasattr(model, "songs"):
            return Markup("<i>(Lỗi model/relation)</i>")
        try:
            songs_to_show = model.songs.limit(5).all()
            if not songs_to_show:
                return Markup("<i>(Trống)</i>")

            song_titles_html = "".join(
                [
                    f"<li>{escape(getattr(song, 'title', 'N/A'))}</li>"
                    for song in songs_to_show
                ]
            )
            html_output = f"<ul style='padding-left: 15px; margin: 0; list-style: disc;'>{song_titles_html}</ul>"

            total_songs = PlaylistAdminView._get_song_count(model)

            if isinstance(total_songs, int):
                if total_songs > 5:
                    html_output += f"<small style='display: block; padding-left: 15px;'>... và {total_songs - 5} bài khác</small>"
            else:
                html_output += f"<small style='display: block; padding-left: 15px;'>(Lỗi đếm)</small>"

            return Markup(html_output)
        except Exception as e:
            print(
                f"[ADMIN_ERROR] Exception in _songs_formatter for playlist ID {getattr(model, 'id', 'N/A')}: {e.__class__.__name__}: {e}"
            )
            traceback.print_exc()
            return Markup("<i>(Lỗi xem trước)</i>")

    column_list = (
        "id",
        "name",
        "owner",
        "_song_count",
        "is_public",
        "created_at",
        "_songs_preview",
        "is_featured",
    )
    column_labels = {
        "id": "ID",
        "name": "Tên Playlist",
        "owner": "Người tạo",
        "_song_count": "Số bài hát",
        "is_public": "Công khai",
        "created_at": "Ngày tạo",
        "_songs_preview": "Xem trước bài hát",
        "is_featured": "Nổi bật",
    }
    column_formatters = {
        "owner": lambda v, c, m, p: m.owner.username if m.owner else "(Không có)",
        "_song_count": lambda v, c, m, p: (
            PlaylistAdminView._get_song_count(m)
            if PlaylistAdminView._get_song_count(m) is not None
            else Markup("<i>Lỗi</i>")
        ),
        "_songs_preview": _songs_formatter,
    }
    column_searchable_list = ("name",)
    column_filters = ("user_id", "is_public", "created_at", "is_featured")
    form_excluded_columns = ("listens", "comments", "created_at")
    can_create = True
    can_edit = True
    can_delete = True
    page_size = 50


class ListeningHistoryAdminView(SecureAdminView):
    column_list = ("id", "listener", "song_played", "timestamp")
    column_labels = {
        "id": "ID",
        "listener": "Người nghe",
        "song_played": "Bài hát đã nghe",
        "timestamp": "Thời gian nghe",
    }
    column_formatters = {
        "listener": lambda v, c, m, p: m.listener.username if m.listener else "N/A",
        "song_played": lambda v, c, m, p: (
            m.song_played.title if m.song_played else "N/A"
        ),
    }
    column_filters = ("timestamp", "user_id", "song_id")
    can_create = False
    can_edit = False
    can_delete = True
    page_size = 50


class CommentAdminView(SecureAdminView):
    column_list = ("id", "text", "timestamp", "author", "song")
    column_formatters = {
        "author": lambda v, c, m, p: m.author.username if m.author else "N/A",
        "song": lambda v, c, m, p: m.song.title if m.song else "N/A",
        "text": lambda v, c, m, p: (
            (m.text[:50] + "...") if m.text and len(m.text) > 50 else m.text
        ),
    }
    column_filters = ("timestamp", "user_id", "song_id")
    can_create = False
    can_edit = True
    can_delete = True
    column_searchable_list = ("text",)


admin = Admin(
    app,
    name="Music App Admin",
    template_mode="bootstrap4",
    index_view=MyAdminIndexView(name="Dashboard", url="/admin"),
)
admin.add_view(UserAdminView(User, db.session, name="Người dùng"))
admin.add_view(SongAdminView(Song, db.session, name="Bài hát"))
admin.add_view(PlaylistAdminView(Playlist, db.session, name="Playlist"))
admin.add_view(
    ListeningHistoryAdminView(ListeningHistory, db.session, name="Lịch sử nghe")
)
admin.add_view(CommentAdminView(Comment, db.session, name="Bình luận"))
admin.add_view(
    ArtistAdminView(Artist, db.session, name="Nghệ sĩ", category="Quản lý Nội dung")
)


@app.route("/song/<int:song_id>/detail")
def song_detail(song_id):

    song = db.session.get(Song, song_id)
    if not song:
        abort(404)

    comments = (
        Comment.query.filter_by(song_id=song.id).order_by(desc(Comment.timestamp)).all()
    )

    return render_template(
        "song_detail.html",
        title=f"{song.title} - {song.artist}",
        song=song,
        comments=comments,
    )


@app.route("/artist/<path:artist_name_url>")
def artist_detail_page(artist_name_url):
    try:
        artist_name_decoded = unquote(artist_name_url, encoding="utf-8")
    except Exception as e:
        current_app.logger.warning(f"Lỗi khi unquote URL '{artist_name_url}': {e}")
        artist_name_decoded = artist_name_url

    normalized_artist_search_name = normalize_text(artist_name_decoded)

    artist_obj = Artist.query.filter(
        Artist.name_normalized == normalized_artist_search_name
    ).first()

    artist_display_name = artist_name_decoded
    artist_bio_final = (
        f"Thông tin giới thiệu về nghệ sĩ {artist_name_decoded} hiện chưa có."
    )
    artist_profile_image_final = None
    artist_banner_image_final = None

    if artist_obj:

        artist_display_name = artist_obj.name
        artist_bio_final = (
            artist_obj.bio
            if artist_obj.bio
            else f"Thông tin giới thiệu về nghệ sĩ {artist_obj.name} hiện chưa có."
        )
        if artist_obj.profile_image_path:
            artist_profile_image_final = artist_obj.profile_image_path
        if artist_obj.banner_image_path:
            artist_banner_image_final = artist_obj.banner_image_path
    else:

        current_app.logger.info(
            f"Không tìm thấy đối tượng Artist cho '{artist_name_decoded}'. Sẽ thử fallback (nếu có)."
        )

        first_song_for_fallback_cover = (
            Song.query.filter(
                Song.artist.ilike(artist_name_decoded),
                Song.cover_art_path.isnot(None),
                Song.cover_art_path != "",
            )
            .order_by(Song.id)
            .first()
        )
        if first_song_for_fallback_cover:
            artist_profile_image_final = first_song_for_fallback_cover.cover_art_path

    songs_by_artist_query = (
        Song.query.filter(
            func.lower(Song.artist_normalized)
            == func.lower(normalize_text(artist_display_name))
        )
        .order_by(Song.title)
        .all()
    )

    song_count = len(songs_by_artist_query)
    songs_data = []

    if song_count > 0:
        song_ids = [s.id for s in songs_by_artist_query]

        likes_counts_query = (
            db.session.query(
                likes.c.song_id, func.count(likes.c.user_id).label("count")
            )
            .filter(likes.c.song_id.in_(song_ids))
            .group_by(likes.c.song_id)
            .all()
        )
        likes_map = {song_id: count for song_id, count in likes_counts_query}

        listens_counts_query = (
            db.session.query(
                ListeningHistory.song_id, func.count(ListeningHistory.id).label("count")
            )
            .filter(ListeningHistory.song_id.in_(song_ids))
            .group_by(ListeningHistory.song_id)
            .all()
        )
        listens_map = {song_id: count for song_id, count in listens_counts_query}

        liked_song_ids_by_current_user = set()
        if current_user.is_authenticated:
            liked_song_ids_by_current_user = {
                row[0]
                for row in db.session.query(likes.c.song_id)
                .filter(
                    likes.c.user_id == current_user.id, likes.c.song_id.in_(song_ids)
                )
                .all()
            }

        for song_item in songs_by_artist_query:
            songs_data.append(
                {
                    "song": song_item,
                    "is_liked_by_current": song_item.id
                    in liked_song_ids_by_current_user,
                    "like_count": likes_map.get(song_item.id, 0),
                    "listen_count": listens_map.get(song_item.id, 0),
                    "duration": song_item.duration,
                }
            )

    default_cover_placeholder_url = url_for(
        "static", filename="img/covers/default_cover.png"
    )

    return render_template(
        "artist_detail.html",
        title=f"Nghệ sĩ: {artist_display_name}",
        artist_name=artist_display_name,
        artist_cover_path=artist_profile_image_final,
        artist_banner_path=artist_banner_image_final,
        artist_bio=artist_bio_final,
        songs_data=songs_data,
        song_count=song_count,
        default_cover_placeholder_url=default_cover_placeholder_url,
    )


@app.route("/")
def index():
    songs_data = []
    artists_for_template = []
    recent_listens_data = []
    featured_playlists_data = []
    current_hour = datetime.now().hour
    greeting = "Chào bạn"
    if 5 <= current_hour < 12:
        greeting = "Chào buổi sáng"
    elif 12 <= current_hour < 18:
        greeting = "Chào buổi chiều"
    elif 18 <= current_hour < 22:
        greeting = "Chào buổi tối"
    else:
        greeting = "Khuya rồi, nghe nhạc thôi"
    possible_general_titles = [
        f"{greeting}! Nghe gì hôm nay?",
        "Dành riêng cho bạn",
        "Khám phá giai điệu mới",
        "Tuyển tập được chọn lọc",
        "Những bài hát không thể bỏ lỡ",
        "Thử những nhịp điệu này",
        "Cảm hứng cho ngày mới",
        "Âm nhạc theo tâm trạng",
        "Bắt đầu ngày mới với âm nhạc",
        "Thư giãn cuối ngày",
        "Một chút giai điệu cho bạn",
        "Đang hot trên Music For Life",
        "Có thể bạn sẽ nghiện",
        "Nghe và cảm nhận",
        "Mở ra thế giới âm nhạc",
        "Dành cho những phút giây của riêng bạn",
        "Những bản hit quen thuộc",
        "Giai điệu vượt thời gian",
        "Mới và hay",
        "Những bài hát được nghe nhiều",
        "Vừa được thêm vào",
        "Chill một chút nhé?",
        "Những gì bạn có thể thích",
        "Âm nhạc cho mọi khoảnh khắc",
        "Vũ trụ âm nhạc của riêng bạn",
        "Lựa chọn từ chúng tôi",
    ]
    dynamic_general_title = random.choice(possible_general_titles)
    possible_featured_playlist_titles = [
        "Tuyển tập không thể bỏ qua",
        "Đang thịnh hành",
        "Những gì mọi người đang nghe",
        "Nghe cùng Music For Life",
        "Các tuyển tập đặc biệt",
        "Tuyển tập cho ngày hôm nay",
        "Những dòng chảy âm nhạc",
        "Khám phá theo gu",
        "Bắt nhịp xu hướng",
        "Nghe là ghiền",
    ]
    dynamic_featured_playlist_title = random.choice(possible_featured_playlist_titles)

    try:

        distinct_artist_names_in_songs = [
            name_tuple[0]
            for name_tuple in db.session.query(distinct(Song.artist))
            .filter(Song.artist.isnot(None), Song.artist != "")
            .order_by(func.random())
            .limit(12)
            .all()
        ]

        if distinct_artist_names_in_songs:

            for artist_name_str in distinct_artist_names_in_songs:
                normalized_name = normalize_text(artist_name_str)
                artist_obj = Artist.query.filter(
                    Artist.name_normalized == normalized_name
                ).first()

                artist_cover_to_display = None
                display_name_for_link = artist_name_str

                if artist_obj:
                    display_name_for_link = artist_obj.name
                    if artist_obj.profile_image_path:
                        artist_cover_to_display = artist_obj.profile_image_path

                artists_for_template.append(
                    {"name": display_name_for_link, "cover": artist_cover_to_display}
                )
        subquery = (
            db.session.query(
                Song.artist,
                Song.cover_art_path,
                func.row_number()
                .over(partition_by=Song.artist, order_by=Song.id)
                .label("rn"),
            )
            .filter(Song.artist != None, Song.artist != "")
            .subquery()
        )

        artist_data_query = (
            db.session.query(subquery.c.artist, subquery.c.cover_art_path)
            .filter(subquery.c.rn == 1)
            .order_by(subquery.c.artist)
            .limit(12)
            .all()
        )
        artists = [
            {"name": artist, "cover": cover} for artist, cover in artist_data_query
        ]

        likes_subquery = (
            db.session.query(
                likes.c.song_id, func.count(likes.c.user_id).label("like_count")
            )
            .group_by(likes.c.song_id)
            .subquery()
        )

        listens_subquery = (
            db.session.query(
                ListeningHistory.song_id,
                func.count(ListeningHistory.id).label("listen_count"),
            )
            .group_by(ListeningHistory.song_id)
            .subquery()
        )

        songs_with_counts = (
            db.session.query(
                Song,
                func.coalesce(likes_subquery.c.like_count, 0).label("like_count"),
                func.coalesce(listens_subquery.c.listen_count, 0).label("listen_count"),
                Song.share_count,
                Song.duration,
            )
            .outerjoin(likes_subquery, Song.id == likes_subquery.c.song_id)
            .outerjoin(listens_subquery, Song.id == listens_subquery.c.song_id)
            .order_by(Song.title)
            .all()
        )

        for song, like_count, listen_count, share_count, duration in songs_with_counts:
            is_liked_by_current = False
            if current_user.is_authenticated:
                is_liked_by_current = current_user.has_liked_song(song)
            songs_data.append(
                {
                    "song": song,
                    "like_count": like_count,
                    "listen_count": listen_count,
                    "share_count": share_count,
                    "is_liked_by_current": is_liked_by_current,
                    "duration": duration,
                }
            )
        if current_user.is_authenticated:
            latest_listen_subquery = (
                db.session.query(
                    ListeningHistory.song_id,
                    func.max(ListeningHistory.timestamp).label("last_played"),
                )
                .filter(ListeningHistory.user_id == current_user.id)
                .group_by(ListeningHistory.song_id)
                .subquery()
            )

            recent_listens_query = (
                db.session.query(Song, latest_listen_subquery.c.last_played)
                .join(
                    latest_listen_subquery, Song.id == latest_listen_subquery.c.song_id
                )
                .order_by(desc(latest_listen_subquery.c.last_played))
                .limit(6)
            )

            recent_song_ids = [song.id for song, _ in recent_listens_query.all()]
            recent_liked_ids = set()
            if recent_song_ids:
                recent_liked_ids = {
                    row[0]
                    for row in db.session.query(likes.c.song_id)
                    .filter(
                        likes.c.user_id == current_user.id,
                        likes.c.song_id.in_(recent_song_ids),
                    )
                    .all()
                }

            for song, last_played in recent_listens_query.all():
                is_liked = song.id in recent_liked_ids
                recent_listens_data.append(
                    {"song": song, "last_played": last_played, "is_liked": is_liked}
                )

        featured_playlists_query = (
            Playlist.query.filter_by(is_public=True, is_featured=True)
            .order_by(desc(Playlist.created_at))
            .limit(6)
        )
        featured_playlists_objects = featured_playlists_query.all()

        if featured_playlists_objects:
            featured_playlist_ids = [p.id for p in featured_playlists_objects]

            featured_covers_map = {}
            featured_song_counts_map = {}
            featured_artists_map = {}

            song_count_results_featured = (
                db.session.query(
                    playlist_songs.c.playlist_id,
                    func.count(playlist_songs.c.song_id).label("total_songs"),
                )
                .filter(playlist_songs.c.playlist_id.in_(featured_playlist_ids))
                .group_by(playlist_songs.c.playlist_id)
                .all()
            )
            for pl_id, count in song_count_results_featured:
                featured_song_counts_map[pl_id] = count

            playlist_ids_needing_song_covers = [
                p.id
                for p in featured_playlists_objects
                if not p.custom_cover_path and featured_song_counts_map.get(p.id, 0) > 0
            ]

            if playlist_ids_needing_song_covers:
                sq_covers_featured = (
                    db.session.query(
                        playlist_songs.c.playlist_id,
                        Song.cover_art_path,
                        func.row_number()
                        .over(
                            partition_by=playlist_songs.c.playlist_id,
                            order_by=playlist_songs.c.song_id,
                        )
                        .label("rn"),
                    )
                    .join(Song, Song.id == playlist_songs.c.song_id)
                    .filter(
                        playlist_songs.c.playlist_id.in_(
                            playlist_ids_needing_song_covers
                        )
                    )
                    .filter(Song.cover_art_path.isnot(None), Song.cover_art_path != "")
                    .subquery()
                )

                cover_results_featured = (
                    db.session.query(
                        sq_covers_featured.c.playlist_id,
                        sq_covers_featured.c.cover_art_path,
                    )
                    .filter(sq_covers_featured.c.rn <= 4)
                    .all()
                )

                for pl_id, cover_path in cover_results_featured:
                    if pl_id not in featured_covers_map:
                        featured_covers_map[pl_id] = []
                    if len(featured_covers_map[pl_id]) < 4:
                        featured_covers_map[pl_id].append(cover_path)

            for pl_obj in featured_playlists_objects:
                pl_id = pl_obj.id
                current_song_count = featured_song_counts_map.get(pl_id, 0)
                if current_song_count > 0:
                    artists_in_playlist_query = (
                        db.session.query(distinct(Song.artist))
                        .join(playlist_songs, Song.id == playlist_songs.c.song_id)
                        .filter(playlist_songs.c.playlist_id == pl_id)
                        .filter(Song.artist.isnot(None), Song.artist != "")
                        .order_by(Song.artist)
                        .limit(7)
                        .all()
                    )
                    featured_artists_map[pl_id] = [
                        artist_tuple[0] for artist_tuple in artists_in_playlist_query
                    ]
                else:
                    featured_artists_map[pl_id] = []

            for pl_obj in featured_playlists_objects:
                featured_playlists_data.append(
                    {
                        "playlist": pl_obj,
                        "covers": featured_covers_map.get(pl_obj.id, []),
                        "song_count": featured_song_counts_map.get(pl_obj.id, 0),
                        "artists": featured_artists_map.get(pl_obj.id, []),
                    }
                )
    except Exception as e:
        current_app.logger.error(f"Error fetching index data: {e}")
        import traceback

        traceback.print_exc()
        flash("Có lỗi xảy ra khi tải dữ liệu trang chủ.", "danger")
        artists_for_template = []
        songs_data = []
        recent_listens_data = []
        featured_playlists_data = []
        dynamic_recommendation_title = "Đề xuất"
        dynamic_featured_playlist_title = "Playlist Nổi Bật"

    return render_template(
        "index.html",
        artists=artists_for_template,
        songs_data=songs_data,
        recent_listens=recent_listens_data,
        featured_playlists=featured_playlists_data,
        general_recommendation_title=dynamic_general_title,
        featured_playlist_section_title=dynamic_featured_playlist_title,
        greeting_message=greeting,
        title="Trang chủ",
    )


@app.route("/register", methods=["GET", "POST"])
def register():

    if current_user.is_authenticated:
        return redirect(url_for("index"))
    form = RegistrationForm()
    if form.validate_on_submit():

        user = User(username=form.username.data, role="user")
        user.set_password(form.password.data)
        try:
            db.session.add(user)
            db.session.commit()
            flash("Đăng ký thành công! Bạn có thể đăng nhập ngay bây giờ.", "success")
            return redirect(url_for("login"))
        except Exception as e:
            db.session.rollback()
            print(f"Error during registration: {e}")
            flash("Đã xảy ra lỗi trong quá trình đăng ký. Vui lòng thử lại.", "danger")

    return render_template("register.html", title="Đăng ký", form=form)


@app.route("/login", methods=["GET", "POST"])
def login():
    if current_user.is_authenticated:
        return redirect(url_for("index"))
    form = LoginForm()
    if form.validate_on_submit():
        user = User.query.filter_by(username=form.username.data).first()

        if user and user.check_password(form.password.data):
            login_user(user)
            next_page = request.args.get("next")
            flash("Đăng nhập thành công!", "success")

            if (
                next_page
                and next_page.startswith(url_for("admin.index", _external=False))
                and user.is_admin()
            ):
                return redirect(next_page)

            return redirect(next_page or url_for("index"))
        else:
            flash(
                "Đăng nhập không thành công. Vui lòng kiểm tra lại tên đăng nhập và mật khẩu.",
                "danger",
            )
    return render_template("login.html", title="Đăng nhập", form=form)


@app.route("/logout")
@login_required
def logout():
    logout_user()
    flash("Bạn đã đăng xuất.", "info")
    return redirect(url_for("index"))


@app.route("/account", methods=["GET", "POST"])
@login_required
def account():
    form = ChangePasswordForm()
    if form.validate_on_submit():

        if current_user.check_password(form.current_password.data):
            current_user.set_password(form.new_password.data)
            try:
                db.session.commit()
                flash("Đã cập nhật mật khẩu thành công!", "success")
                return redirect(url_for("account"))
            except Exception as e:
                db.session.rollback()
                print(f"Error changing password: {e}")
                flash("Đã xảy ra lỗi khi cập nhật mật khẩu.", "danger")
        else:
            flash("Mật khẩu hiện tại không đúng.", "danger")

    listening_history = (
        ListeningHistory.query.filter_by(user_id=current_user.id)
        .order_by(desc(ListeningHistory.timestamp))
        .limit(20)
        .all()
    )

    return render_template(
        "account.html",
        title="Tài khoản của tôi",
        form=form,
        listening_history=listening_history,
    )


@app.route("/playlist/create", methods=["GET", "POST"])
@login_required
def create_playlist():
    form = CreatePlaylistForm()
    if form.validate_on_submit():
        new_name = form.name.data

        existing_playlist = Playlist.query.filter_by(
            user_id=current_user.id, name=new_name
        ).first()
        if existing_playlist:
            form.name.errors.append(
                "Bạn đã có một playlist với tên này. Vui lòng chọn tên khác."
            )

        if form.name.errors:
            return render_template(
                "create_playlist.html", title="Tạo Playlist Mới", form=form
            )

        new_playlist = Playlist(
            name=new_name, owner=current_user, is_public=form.is_public.data
        )

        db.session.add(new_playlist)
        db.session.flush()

        uploaded_cover_path = None
        cover_file = form.cover_image.data
        if cover_file:

            uploaded_cover_path = save_playlist_cover(cover_file, new_playlist)
            if uploaded_cover_path:
                new_playlist.custom_cover_path = uploaded_cover_path
            else:

                db.session.rollback()

                return render_template(
                    "create_playlist.html", title="Tạo Playlist Mới", form=form
                )

        try:
            db.session.commit()
            flash(f'Playlist "{new_playlist.name}" đã được tạo!', "success")

            return redirect(url_for("playlists"))
        except Exception as e:
            db.session.rollback()
            print(f"Error committing new playlist: {e}")
            flash("Đã xảy ra lỗi khi tạo playlist.", "danger")

    return render_template("create_playlist.html", title="Tạo Playlist Mới", form=form)


@app.route("/account/edit", methods=["GET", "POST"])
@login_required
def edit_profile():
    form = ProfileEditForm(obj=current_user)
    if form.validate_on_submit():
        try:

            old_avatar_rel_path = current_user.avatar_url

            current_user.display_name = form.display_name.data
            current_user.bio = form.bio.data
            current_user.profile_visibility = form.profile_visibility.data

            avatar_file = form.avatar.data
            if avatar_file:

                filename_ext = avatar_file.filename.rsplit(".", 1)[1].lower()
                filename = secure_filename(f"avatar_{current_user.id}.{filename_ext}")
                save_path = os.path.join(AVATAR_FOLDER_PATH, filename)

                new_avatar_rel_path = os.path.join(
                    "img", AVATAR_FOLDER_NAME, filename
                ).replace("\\", "/")
                if old_avatar_rel_path and old_avatar_rel_path != new_avatar_rel_path:
                    try:
                        old_avatar_full_path = os.path.join(
                            static_folder_path, old_avatar_rel_path
                        )
                        if os.path.exists(old_avatar_full_path):
                            os.remove(old_avatar_full_path)
                            print(f"Deleted old avatar: {old_avatar_full_path}")
                    except Exception as e_del:
                        print(
                            f"Warning: Could not delete old avatar {old_avatar_rel_path}: {e_del}"
                        )

                avatar_file.save(save_path)
                print(f"Saved new avatar to: {save_path}")

                current_user.avatar_url = new_avatar_rel_path

            db.session.commit()
            flash("Đã cập nhật hồ sơ thành công!", "success")
            return redirect(url_for("user_profile", username=current_user.username))
        except Exception as e:
            db.session.rollback()
            flash(f"Lỗi khi cập nhật hồ sơ: {e}", "danger")
            print(f"Error updating profile: {e}")
    elif request.method == "POST":

        flash("Cập nhật hồ sơ thất bại. Vui lòng kiểm tra lại các trường.", "warning")

    current_avatar_rel_path = (
        current_user.avatar_url or "img/avatars/default_avatar.png"
    )

    current_avatar_url = url_for("static", filename=current_avatar_rel_path)
    return render_template(
        "edit_profile.html",
        title="Chỉnh sửa Hồ sơ",
        form=form,
        current_avatar=current_avatar_url,
    )


@app.route("/user/<username>")
def user_profile(username):
    profile_user = User.query.filter_by(username=username).first_or_404()
    show_private_content = False

    if profile_user.profile_visibility == "public":
        show_private_content = True
    elif current_user.is_authenticated and current_user.id == profile_user.id:
        show_private_content = True

    profile_playlists_data = []
    recent_activity = []

    if show_private_content:

        playlist_query = Playlist.query.filter_by(user_id=profile_user.id)
        if not (current_user.is_authenticated and current_user.id == profile_user.id):
            playlist_query = playlist_query.filter_by(is_public=True)

        playlist_query = playlist_query.order_by(desc(Playlist.created_at))

        playlists_to_display = playlist_query.all()
        playlist_ids_to_display = [p.id for p in playlists_to_display]

        covers_map = {}
        song_counts_map = {}

        if playlist_ids_to_display:

            sq_covers = (
                db.session.query(
                    playlist_songs.c.playlist_id,
                    Song.cover_art_path,
                    func.row_number()
                    .over(
                        partition_by=playlist_songs.c.playlist_id,
                        order_by=playlist_songs.c.song_id,
                    )
                    .label("rn"),
                )
                .join(Song, Song.id == playlist_songs.c.song_id)
                .filter(playlist_songs.c.playlist_id.in_(playlist_ids_to_display))
                .filter(Song.cover_art_path.isnot(None), Song.cover_art_path != "")
                .subquery()
            )

            cover_results = (
                db.session.query(sq_covers.c.playlist_id, sq_covers.c.cover_art_path)
                .filter(sq_covers.c.rn <= 4)
                .all()
            )

            for pl_id, cover_path in cover_results:
                if pl_id not in covers_map:
                    covers_map[pl_id] = []
                if len(covers_map[pl_id]) < 4:
                    covers_map[pl_id].append(cover_path)

            song_count_results = (
                db.session.query(
                    playlist_songs.c.playlist_id, func.count(playlist_songs.c.song_id)
                )
                .filter(playlist_songs.c.playlist_id.in_(playlist_ids_to_display))
                .group_by(playlist_songs.c.playlist_id)
                .all()
            )

            for pl_id, count in song_count_results:
                song_counts_map[pl_id] = count

        for pl in playlists_to_display:
            profile_playlists_data.append(
                {
                    "playlist": pl,
                    "covers": covers_map.get(pl.id, []),
                    "song_count": song_counts_map.get(pl.id, 0),
                }
            )

        recent_activity = (
            ListeningHistory.query.filter_by(user_id=profile_user.id)
            .order_by(desc(ListeningHistory.timestamp))
            .limit(15)
            .all()
        )

    elif profile_user.profile_visibility == "private":

        pass

    return render_template(
        "user_profile.html",
        title=f"Hồ sơ {profile_user.name_display}",
        profile_user=profile_user,
        show_private_content=show_private_content,
        profile_playlists_data=profile_playlists_data,
        recent_activity=recent_activity,
    )


@app.route("/playlists")
@login_required
def playlists():

    privacy_filter = request.args.get("privacy", "all")
    search_term = request.args.get("search", "").strip()
    sort_key = request.args.get("sort", "date_new")

    base_query = Playlist.query.filter_by(user_id=current_user.id)

    if privacy_filter == "public":
        base_query = base_query.filter(Playlist.is_public == True)
    elif privacy_filter == "private":
        base_query = base_query.filter(Playlist.is_public == False)

    if search_term:

        base_query = base_query.filter(Playlist.name.ilike(f"%{search_term}%"))

    order_criteria = [desc(Playlist.is_pinned)]

    if sort_key == "name_asc":
        order_criteria.append(asc(Playlist.name))
    elif sort_key == "name_desc":
        order_criteria.append(desc(Playlist.name))
    elif sort_key == "date_old":
        order_criteria.append(asc(Playlist.created_at))
    else:
        sort_key = "date_new"
        order_criteria.append(desc(Playlist.created_at))

    base_query = base_query.order_by(*order_criteria)

    user_playlists = base_query.all()
    user_playlist_ids = [p.id for p in user_playlists]

    covers_map = {}
    song_counts_map = {}
    if user_playlist_ids:

        sq_covers = (
            db.session.query(
                playlist_songs.c.playlist_id,
                Song.cover_art_path,
                func.row_number()
                .over(
                    partition_by=playlist_songs.c.playlist_id,
                    order_by=playlist_songs.c.song_id,
                )
                .label("rn"),
            )
            .join(Song, Song.id == playlist_songs.c.song_id)
            .filter(playlist_songs.c.playlist_id.in_(user_playlist_ids))
            .filter(Song.cover_art_path.isnot(None), Song.cover_art_path != "")
            .subquery()
        )
        cover_results = (
            db.session.query(sq_covers.c.playlist_id, sq_covers.c.cover_art_path)
            .filter(sq_covers.c.rn <= 4)
            .all()
        )
        for pl_id, cover_path in cover_results:
            if pl_id not in covers_map:
                covers_map[pl_id] = []
            if len(covers_map[pl_id]) < 4:
                covers_map[pl_id].append(cover_path)

        song_count_results = (
            db.session.query(
                playlist_songs.c.playlist_id,
                func.count(playlist_songs.c.song_id).label("total_songs"),
            )
            .filter(playlist_songs.c.playlist_id.in_(user_playlist_ids))
            .group_by(playlist_songs.c.playlist_id)
            .all()
        )
        for pl_id, count in song_count_results:
            song_counts_map[pl_id] = count

    playlists_data = []
    for pl in user_playlists:
        playlists_data.append(
            {
                "playlist": pl,
                "covers": covers_map.get(pl.id, []),
                "song_count": song_counts_map.get(pl.id, 0),
            }
        )

    return render_template(
        "playlists.html",
        playlists_data=playlists_data,
        title="Thư viện của tôi",
        current_privacy=privacy_filter,
        current_search=search_term,
        current_sort=sort_key,
    )


@app.route("/playlist/<int:playlist_id>/edit", methods=["GET", "POST"])
@login_required
def edit_playlist(playlist_id):

    playlist = Playlist.query.get_or_404(playlist_id)

    if playlist.user_id != current_user.id:
        flash("Bạn không có quyền chỉnh sửa playlist này.", "danger")
        return redirect(url_for("playlists"))

    form = CreatePlaylistForm(obj=playlist)

    if form.validate_on_submit():
        new_name = form.name.data

        existing_playlist_with_new_name = Playlist.query.filter(
            Playlist.user_id == current_user.id,
            Playlist.name == new_name,
            Playlist.id != playlist_id,
        ).first()
        if existing_playlist_with_new_name:
            form.name.errors.append(
                "Bạn đã có một playlist khác với tên này rồi. Vui lòng chọn tên khác."
            )

            current_custom_cover = playlist.custom_cover_path
            return render_template(
                "edit_playlist.html",
                title=f"Sửa Playlist: {playlist.name}",
                form=form,
                playlist=playlist,
                current_cover=current_custom_cover,
            )

        new_cover_file = form.cover_image.data
        new_cover_path = None
        should_delete_old_cover = False
        old_cover_path_to_delete = playlist.custom_cover_path

        if form.remove_cover.data:
            print(f"[EDIT POST] User requested REMOVE cover for playlist {playlist_id}")
            if old_cover_path_to_delete:
                should_delete_old_cover = True
                playlist.custom_cover_path = None
                print(
                    f"[EDIT POST] Custom cover path set to None. Old path flagged for deletion: {old_cover_path_to_delete}"
                )
            else:

                playlist.custom_cover_path = None
                print(
                    "[EDIT POST] No existing custom cover to remove, ensuring path is None."
                )

        elif form.cover_image.data:
            new_cover_file = form.cover_image.data
            print(f"[EDIT POST] New cover file provided: {new_cover_file.filename}")
            new_cover_path = save_playlist_cover(new_cover_file, playlist)
            if new_cover_path:
                print(f"[EDIT POST] Saved new cover path: {new_cover_path}")
                playlist.custom_cover_path = new_cover_path

                if (
                    old_cover_path_to_delete
                    and old_cover_path_to_delete != new_cover_path
                ):
                    should_delete_old_cover = True
                    print(
                        f"[EDIT POST] Flagging different old cover for deletion: {old_cover_path_to_delete}"
                    )
            else:

                print(f"[EDIT POST] Failed to save new cover file.")
                current_custom_cover = playlist.custom_cover_path
                return render_template(
                    "edit_playlist.html",
                    title=f"Sửa Playlist: {playlist.name}",
                    form=form,
                    playlist=playlist,
                    current_cover=current_custom_cover,
                )

        playlist.name = new_name
        playlist.is_public = form.is_public.data
        print(
            f"[EDIT POST] Playlist object before commit: ID={playlist.id}, Name='{playlist.name}', CustomCover='{playlist.custom_cover_path}'"
        )

        try:
            print("[EDIT POST] Attempting commit...")
            db.session.commit()
            print("[EDIT POST] Commit successful.")

            if should_delete_old_cover:
                print(
                    f"[EDIT POST] Attempting to delete flagged old cover: {old_cover_path_to_delete}"
                )
                delete_file(old_cover_path_to_delete)

            flash(f'Đã cập nhật playlist "{playlist.name}"!', "success")
            return redirect(url_for("playlist_detail", playlist_id=playlist.id))
        except Exception as e:
            db.session.rollback()
            print(f"[EDIT POST] !!! COMMIT FAILED: {e}")

            if not form.remove_cover.data and new_cover_path:
                print(
                    f"[EDIT POST] Rolling back: Deleting newly saved file due to commit error: {new_cover_path}"
                )
                delete_file(new_cover_path)
            flash("Đã xảy ra lỗi khi cập nhật playlist.", "danger")

            current_custom_cover = old_cover_path_to_delete
            form.name.data = playlist.name
            form.is_public.data = playlist.is_public
            return render_template(
                "edit_playlist.html",
                title=f"Sửa Playlist: {playlist.name}",
                form=form,
                playlist=playlist,
                current_cover=current_custom_cover,
            )

    current_custom_cover = playlist.custom_cover_path
    return render_template(
        "edit_playlist.html",
        title=f"Sửa Playlist: {playlist.name}",
        form=form,
        playlist=playlist,
        current_cover=current_custom_cover,
    )


@app.route("/playlist/<int:playlist_id>")
@login_required
def playlist_detail(playlist_id):
    playlist = Playlist.query.get_or_404(playlist_id)
    can_view_private_content = False

    if playlist.is_public:
        can_view_private_content = True
    elif current_user.is_authenticated and current_user.id == playlist.user_id:
        can_view_private_content = True

    if not can_view_private_content:
        flash("Playlist này là riêng tư hoặc bạn không có quyền xem.", "warning")
        return redirect(url_for("index"))

    songs_data = []
    song_count = 0
    total_playlist_duration_seconds = 0
    playlist_contributing_artists = []
    covers = []

    try:
        song_count_query = db.session.query(
            func.count(playlist_songs.c.song_id)
        ).filter(playlist_songs.c.playlist_id == playlist_id)
        song_count = song_count_query.scalar() or 0

        if song_count > 0:
            duration_sum_result = (
                db.session.query(func.sum(Song.duration))
                .join(playlist_songs, Song.id == playlist_songs.c.song_id)
                .filter(playlist_songs.c.playlist_id == playlist_id)
                .filter(Song.duration != None)
                .scalar()
            )
            total_playlist_duration_seconds = (
                duration_sum_result if duration_sum_result else 0
            )

            songs_in_playlist_objs = (
                db.session.query(Song)
                .join(playlist_songs, Song.id == playlist_songs.c.song_id)
                .filter(playlist_songs.c.playlist_id == playlist_id)
                .order_by(playlist_songs.c.song_id.asc())
                .all()
            )

            song_ids_in_playlist = [s.id for s in songs_in_playlist_objs]

            listen_counts_map = {}
            if song_ids_in_playlist:
                listen_counts_query = (
                    db.session.query(
                        ListeningHistory.song_id,
                        func.count(ListeningHistory.id).label("count"),
                    )
                    .filter(ListeningHistory.song_id.in_(song_ids_in_playlist))
                    .group_by(ListeningHistory.song_id)
                    .all()
                )
                listen_counts_map = {
                    song_id: count for song_id, count in listen_counts_query
                }

            liked_song_ids_by_current_user = set()
            if current_user.is_authenticated and song_ids_in_playlist:
                liked_song_ids_by_current_user = {
                    row[0]
                    for row in db.session.query(likes.c.song_id)
                    .filter(
                        likes.c.user_id == current_user.id,
                        likes.c.song_id.in_(song_ids_in_playlist),
                    )
                    .all()
                }

            for song_obj in songs_in_playlist_objs:
                is_liked = song_obj.id in liked_song_ids_by_current_user
                actual_listen_count = listen_counts_map.get(song_obj.id, 0)
                songs_data.append(
                    {
                        "song": song_obj,
                        "like_count": song_obj.like_count,
                        "listen_count": actual_listen_count,
                        "share_count": song_obj.share_count,
                        "is_liked_by_current": is_liked,
                        "duration": song_obj.duration,
                    }
                )

            if not playlist.custom_cover_path:
                sq_covers = (
                    db.session.query(
                        Song.cover_art_path,
                        func.row_number()
                        .over(
                            partition_by=playlist_songs.c.playlist_id,
                            order_by=playlist_songs.c.song_id,
                        )
                        .label("rn"),
                    )
                    .join(playlist_songs, Song.id == playlist_songs.c.song_id)
                    .filter(playlist_songs.c.playlist_id == playlist_id)
                    .filter(Song.cover_art_path.isnot(None), Song.cover_art_path != "")
                    .limit(4)
                    .subquery()
                )
                cover_results = db.session.query(sq_covers.c.cover_art_path).all()
                covers = [cover[0] for cover in cover_results]

            distinct_artist_names_in_playlist = (
                db.session.query(distinct(Song.artist))
                .join(playlist_songs, Song.id == playlist_songs.c.song_id)
                .filter(playlist_songs.c.playlist_id == playlist_id)
                .filter(Song.artist.isnot(None), Song.artist != "")
                .order_by(Song.artist)
                .limit(5)
                .all()
            )

            for name_tuple in distinct_artist_names_in_playlist:
                artist_name_str = name_tuple[0]
                artist_avatar_url_final = None

                artist_obj = Artist.query.filter(
                    Artist.name_normalized == normalize_text(artist_name_str)
                ).first()

                if artist_obj and artist_obj.profile_image_path:

                    artist_avatar_url_final = url_for(
                        "static", filename=artist_obj.profile_image_path, _external=True
                    )
                else:

                    song_of_this_artist_with_cover = Song.query.filter(
                        Song.artist.ilike(artist_name_str),
                        Song.cover_art_path.isnot(None),
                        Song.cover_art_path != "",
                    ).first()
                    if (
                        song_of_this_artist_with_cover
                        and song_of_this_artist_with_cover.cover_art_path
                    ):
                        artist_avatar_url_final = url_for(
                            "static",
                            filename=song_of_this_artist_with_cover.cover_art_path,
                            _external=True,
                        )
                    else:

                        artist_avatar_url_final = url_for(
                            "static",
                            filename="img/avatars/default_avatar.png",
                            _external=True,
                        )

                playlist_contributing_artists.append(
                    {
                        "name": artist_name_str,
                        "avatar_url": artist_avatar_url_final,
                        "profile_url": url_for(
                            "artist_detail_page", artist_name_url=artist_name_str
                        ),
                    }
                )

    except Exception as e:
        current_app.logger.error(
            f"Error fetching playlist details for ID {playlist_id}: {e}"
        )
        import traceback

        traceback.print_exc()
        flash("Có lỗi xảy ra khi tải chi tiết playlist.", "danger")
        songs_data = []
        song_count = 0
        covers = []
        total_playlist_duration_seconds = 0
        playlist_contributing_artists = []

    default_cover_placeholder_url = url_for(
        "static", filename="img/covers/default_cover.png"
    )
    default_empty_playlist_image_url = url_for(
        "static", filename="img/covers/default_empty_playlist.png"
    )
    default_icon_svg = Markup(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-16 h-16 text-gray-400"><path fill-rule="evenodd" d="M3 6a3 3 0 0 1 3-3h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6Zm14.25 9.75a.75.75 0 0 0 .75-.75V12a.75.75 0 0 0-1.5 0v3a.75.75 0 0 0 .75.75Zm-4.5-.75a.75.75 0 0 1-.75.75h-3a.75.75 0 0 1 0-1.5h3a.75.75 0 0 1 .75.75Z" clip-rule="evenodd" /><path d="M11.283 6.993c.19-.396.586-.643 1.033-.643h.198c.447 0 .843.247 1.033.643l3.183 6.606c.318.66-.165 1.394-.899 1.394h-6.366c-.734 0-1.217-.734-.899-1.394l3.182-6.606Z" /></svg>'
    )

    return render_template(
        "playlist_detail.html",
        playlist=playlist,
        songs_data=songs_data,
        covers=covers,
        song_count=song_count,
        total_duration_seconds=total_playlist_duration_seconds,
        contributing_artists=playlist_contributing_artists,
        default_cover_placeholder_url=default_cover_placeholder_url,
        default_empty_playlist_image_url=default_empty_playlist_image_url,
        default_icon_svg=default_icon_svg,
        title=f"Playlist: {playlist.name}",
    )


@app.route("/api/song/<int:song_id>/lyrics", methods=["GET"])
def get_lyrics(song_id):
    song = Song.query.get_or_404(song_id)
    if song.lyrics:

        return jsonify({"lyrics": song.lyrics})
    else:

        return jsonify({"lyrics": None}), 200


@app.route("/song/<int:song_id>/add-to-playlist", methods=["GET", "POST"])
@login_required
def add_song_to_playlist_view(song_id):
    song = Song.query.get_or_404(song_id)

    user_playlists = (
        Playlist.query.filter_by(user_id=current_user.id).order_by(Playlist.name).all()
    )

    if request.method == "POST":
        playlist_ids = request.form.getlist("playlist_ids")
        if not playlist_ids:
            flash("Vui lòng chọn ít nhất một playlist.", "warning")

            return render_template(
                "add_song_to_playlist.html",
                song=song,
                playlists=user_playlists,
                title=f'Thêm "{song.title}" vào Playlist',
            )

        playlists_to_add = Playlist.query.filter(
            Playlist.id.in_(playlist_ids), Playlist.user_id == current_user.id
        ).all()

        added_count = 0
        already_present_count = 0
        playlists_added_to = []
        playlists_already_in = []

        for playlist in playlists_to_add:

            if song not in playlist.songs:
                playlist.songs.append(song)
                added_count += 1
                playlists_added_to.append(playlist.name)
            else:
                already_present_count += 1
                playlists_already_in.append(playlist.name)

        if added_count > 0:
            try:
                db.session.commit()
                flash(
                    f'Đã thêm bài hát "{song.title}" vào playlist: {", ".join(playlists_added_to)}.',
                    "success",
                )
                if already_present_count > 0:
                    flash(
                        f'Lưu ý: Bài hát đã có sẵn trong các playlist: {", ".join(playlists_already_in)}.',
                        "info",
                    )
            except Exception as e:
                db.session.rollback()
                print(f"Error adding song to playlists: {e}")
                flash("Đã xảy ra lỗi khi thêm bài hát vào playlist.", "danger")

        elif already_present_count > 0:

            flash(
                f'Bài hát "{song.title}" đã có sẵn trong các playlist đã chọn: {", ".join(playlists_already_in)}.',
                "info",
            )
        else:

            flash(
                "Không có playlist nào được chọn hoặc không có thay đổi.", "secondary"
            )

        return redirect(url_for("playlists"))

    return render_template(
        "add_song_to_playlist.html",
        song=song,
        playlists=user_playlists,
        title=f'Thêm "{song.title}" vào Playlist',
    )


@app.route("/playlist/<int:playlist_id>/remove/<int:song_id>", methods=["POST"])
@login_required
def remove_song_from_playlist(playlist_id, song_id):
    playlist = Playlist.query.get_or_404(playlist_id)
    song = Song.query.get_or_404(song_id)

    if playlist.user_id != current_user.id:
        flash("Bạn không có quyền thực hiện thao tác này.", "danger")
        return redirect(url_for("playlists"))

    if song in playlist.songs:
        playlist.songs.remove(song)
        try:
            db.session.commit()
            flash(
                f'Đã xóa bài hát "{song.title}" khỏi playlist "{playlist.name}".',
                "success",
            )
        except Exception as e:
            db.session.rollback()
            print(f"Error removing song from playlist: {e}")
            flash("Đã xảy ra lỗi khi xóa bài hát khỏi playlist.", "danger")
    else:
        flash("Bài hát không có trong playlist này.", "warning")

    return redirect(url_for("playlist_detail", playlist_id=playlist_id))


@app.route("/playlist/<int:playlist_id>/delete", methods=["POST"])
@login_required
def delete_playlist(playlist_id):
    playlist = Playlist.query.get_or_404(playlist_id)

    if playlist.user_id != current_user.id:
        flash("Bạn không có quyền xóa playlist này.", "danger")
        return redirect(url_for("playlists"))

    playlist_name = playlist.name
    try:
        db.session.delete(playlist)
        db.session.commit()
        flash(f'Đã xóa playlist "{playlist_name}".', "success")
    except Exception as e:
        db.session.rollback()
        print(f"Error deleting playlist: {e}")
        flash("Đã xảy ra lỗi khi xóa playlist.", "danger")

    return redirect(url_for("playlists"))


@app.route("/api/playlist/<int:playlist_id>/toggle_pin", methods=["POST"])
@login_required
def toggle_pin_playlist_api(playlist_id):
    playlist = Playlist.query.get(playlist_id)

    if not playlist:

        current_app.logger.warn(
            f"API toggle_pin: Playlist ID {playlist_id} không tìm thấy."
        )
        return jsonify({"status": "error", "message": "Playlist không tồn tại."}), 404

    if playlist.user_id != current_user.id:
        current_app.logger.warn(
            f"API toggle_pin: User {current_user.id} không có quyền với playlist {playlist_id}."
        )
        return (
            jsonify(
                {"status": "error", "message": "Không có quyền thực hiện thao tác này."}
            ),
            403,
        )

    try:
        playlist.is_pinned = not playlist.is_pinned
        db.session.commit()
        action = "ghim" if playlist.is_pinned else "bỏ ghim"
        current_app.logger.info(
            f"API toggle_pin: User {current_user.id} đã {action} playlist {playlist_id} ('{playlist.name}'). Trạng thái is_pinned mới: {playlist.is_pinned}"
        )
        return (
            jsonify(
                {
                    "status": "success",
                    "message": f'Đã {action} playlist "{playlist.name}".',
                    "is_pinned": playlist.is_pinned,
                }
            ),
            200,
        )
    except Exception as e:
        db.session.rollback()
        current_app.logger.error(
            f"API toggle_pin: Lỗi khi ghim/bỏ ghim playlist {playlist_id} cho user {current_user.id}: {e}",
            exc_info=True,
        )
        return (
            jsonify(
                {
                    "status": "error",
                    "message": "Lỗi máy chủ khi cập nhật trạng thái ghim.",
                }
            ),
            500,
        )


@app.route("/api/user/<int:follower_id>/remove_follower", methods=["DELETE"])
@login_required
def remove_follower_api(follower_id):
    """
    API cho phép current_user xóa một follower_id khỏi danh sách người theo dõi họ.
    Thực chất là làm cho follower_id ngừng theo dõi current_user.
    """
    follower_to_remove = db.session.get(User, follower_id)

    if not follower_to_remove:
        return (
            jsonify({"status": "error", "message": "Người theo dõi không tồn tại."}),
            404,
        )

    if follower_to_remove.is_following(current_user):
        try:
            follower_to_remove.unfollow(current_user)
            db.session.commit()
            current_user_follower_count = current_user.followers_count
            return (
                jsonify(
                    {
                        "status": "success",
                        "message": f"Đã xóa {follower_to_remove.name_display} khỏi danh sách người theo dõi.",
                        "current_user_follower_count": current_user_follower_count,
                    }
                ),
                200,
            )
        except Exception as e:
            db.session.rollback()
            print(f"Lỗi khi xóa follower {follower_id} cho user {current_user.id}: {e}")
            return (
                jsonify(
                    {
                        "status": "error",
                        "message": "Lỗi máy chủ khi xóa người theo dõi.",
                    }
                ),
                500,
            )
    else:

        return (
            jsonify(
                {
                    "status": "error",
                    "message": f"{follower_to_remove.name_display} không có trong danh sách người theo dõi của bạn.",
                }
            ),
            400,
        )


@app.route("/api/user/avatar/upload", methods=["POST"])
@login_required
def upload_avatar_api():

    if "avatar" not in request.files:
        return (
            jsonify(
                {"status": "error", "message": "Không tìm thấy file nào được gửi lên."}
            ),
            400,
        )

    file = request.files["avatar"]

    if file.filename == "":
        return jsonify({"status": "error", "message": "Chưa chọn file nào."}), 400

    allowed_extensions = app.config.get(
        "ALLOWED_IMAGE_EXTENSIONS", {"png", "jpg", "jpeg", "gif", "webp"}
    )

    if file and allowed_file(file.filename, allowed_extensions):
        try:
            old_avatar_rel_path = current_user.avatar_url
            filename_ext = file.filename.rsplit(".", 1)[1].lower()

            filename = secure_filename(f"avatar_{current_user.id}.{filename_ext}")
            save_path = os.path.join(AVATAR_FOLDER_PATH, filename)
            new_avatar_rel_path = os.path.join(
                "img", AVATAR_FOLDER_NAME, filename
            ).replace("\\", "/")

            if old_avatar_rel_path and old_avatar_rel_path != new_avatar_rel_path:
                try:
                    old_avatar_full_path = os.path.join(
                        static_folder_path, old_avatar_rel_path
                    )
                    if os.path.exists(old_avatar_full_path):
                        os.remove(old_avatar_full_path)
                        print(f"Deleted old avatar via API: {old_avatar_full_path}")
                except Exception as e_del:
                    print(
                        f"Warning: Could not delete old avatar {old_avatar_rel_path} via API: {e_del}"
                    )

            file.save(save_path)
            print(f"Saved new avatar via API to: {save_path}")

            current_user.avatar_url = new_avatar_rel_path
            db.session.commit()

            return (
                jsonify(
                    {
                        "status": "success",
                        "message": "Tải ảnh đại diện lên thành công!",
                        "new_avatar_url": url_for(
                            "static", filename=new_avatar_rel_path
                        ),
                    }
                ),
                200,
            )

        except Exception as e:
            db.session.rollback()

            print(f"---!!! AVATAR UPLOAD API ERROR !!!---")
            import traceback

            traceback.print_exc()
            print(f"------------------------------------")
            return (
                jsonify({"status": "error", "message": "Lỗi máy chủ khi lưu ảnh."}),
                500,
            )
    else:

        return (
            jsonify(
                {
                    "status": "error",
                    "message": "Loại file ảnh không được hỗ trợ hoặc file không hợp lệ.",
                }
            ),
            400,
        )


@app.route("/api/user/<int:user_id>/follow", methods=["POST", "DELETE"])
@login_required
def follow_user_api(user_id):

    user_to_modify = db.session.get(User, user_id)

    if not user_to_modify:
        return jsonify({"status": "error", "message": "Người dùng không tồn tại."}), 404
    if user_to_modify.id == current_user.id:
        return (
            jsonify(
                {"status": "error", "message": "Bạn không thể tự theo dõi chính mình."}
            ),
            400,
        )

    action_performed = False
    message = ""
    action = "unknown"
    status_code = 200
    followed_user_details = None
    current_user_details_for_list = None

    try:
        if request.method == "POST":
            if not current_user.is_following(user_to_modify):
                current_user.follow(user_to_modify)
                db.session.commit()
                action_performed = True
                message = f"Đã bắt đầu theo dõi {user_to_modify.name_display}."
                action = "followed"
                status_code = 201
                followed_user_details = {
                    "id": user_to_modify.id,
                    "username": user_to_modify.username,
                    "name_display": user_to_modify.name_display,
                    "avatar_url": (
                        url_for("static", filename=user_to_modify.avatar_url)
                        if user_to_modify.avatar_url
                        else url_for(
                            "static", filename="img/avatars/default_avatar.png"
                        )
                    ),
                }
                current_user_details_for_list = {
                    "id": current_user.id,
                    "username": current_user.username,
                    "name_display": current_user.name_display,
                    "avatar_url": (
                        url_for(
                            "static", filename=current_user.avatar_url, _external=False
                        )
                        if current_user.avatar_url
                        else url_for(
                            "static",
                            filename="img/avatars/default_avatar.png",
                            _external=False,
                        )
                    ),
                }
            else:
                message = f"Bạn đang theo dõi {user_to_modify.name_display} rồi."
                action = "already_following"
                status_code = 200

        elif request.method == "DELETE":
            if current_user.is_following(user_to_modify):
                current_user.unfollow(user_to_modify)
                db.session.commit()
                action_performed = True
                message = f"Đã bỏ theo dõi {user_to_modify.name_display}."
                action = "unfollowed"
                status_code = 200
                current_user_details_for_list = {"id": current_user.id}
            else:
                message = f"Bạn chưa theo dõi {user_to_modify.name_display}."
                action = "not_following"
                status_code = 200

        target_user_follower_count = user_to_modify.followers_count
        current_user_following_count = current_user.following_count

        response_data = {
            "status": "success" if action_performed else "no_change",
            "action": action,
            "message": message,
            "target_user_follower_count": target_user_follower_count,
            "current_user_following_count": current_user_following_count,
            "followed_user_details": followed_user_details,
            "current_user_details_for_list": current_user_details_for_list,
        }
        return jsonify(response_data), status_code

    except Exception as e:
        db.session.rollback()
        print(f"Lỗi khi follow/unfollow user {user_id}: {e}")
        return (
            jsonify({"status": "error", "message": "Đã xảy ra lỗi phía máy chủ."}),
            500,
        )


@app.route("/api/search")
def api_search():
    query = request.args.get("q", "").strip()
    search_type = request.args.get("type", "all").lower()

    if not query or len(query) < 1:
        return jsonify(
            {
                "results_type": "mixed",
                "songs": [],
                "artists": [],
                "users": [],
                "filter_applied": search_type,
            }
        )

    normalized_query = normalize_text(query)
    search_term_normalized = f"%{normalized_query}%"
    print(
        f"[API Search] Original: '{query}', Normalized: '{normalized_query}', Type: {search_type}"
    )

    songs_results = []
    artists_results = []
    users_results = []

    try:

        if search_type == "user" or search_type == "all":
            print(f"[API Search] Searching users...")

            user_query = (
                db.session.query(User)
                .filter(
                    or_(
                        User.username_normalized.ilike(search_term_normalized),
                        db.and_(
                            User.display_name_normalized != None,
                            User.display_name_normalized.ilike(search_term_normalized),
                        ),
                    )
                )
                .limit(10 if search_type == "user" else 5)
            )

            for user in user_query.all():
                users_results.append(
                    {
                        "id": user.id,
                        "username": user.username,
                        "name_display": user.name_display,
                        "avatar_url": (
                            url_for("static", filename=user.avatar_url, _external=True)
                            if user.avatar_url
                            else url_for(
                                "static",
                                filename="img/avatars/default_avatar.png",
                                _external=True,
                            )
                        ),
                    }
                )
            if search_type == "user":
                return jsonify(
                    {
                        "results_type": "users",
                        "results": users_results,
                        "filter_applied": "user",
                    }
                )

        if search_type == "song" or search_type == "all":
            print(f"[API Search] Searching songs...")

            likes_subquery = (
                db.session.query(
                    likes.c.song_id, func.count(likes.c.user_id).label("like_count")
                )
                .group_by(likes.c.song_id)
                .subquery()
            )
            listens_subquery = (
                db.session.query(
                    ListeningHistory.song_id,
                    func.count(ListeningHistory.id).label("listen_count"),
                )
                .group_by(ListeningHistory.song_id)
                .subquery()
            )
            song_filter = Song.title_normalized.ilike(search_term_normalized)
            if search_type == "all":
                song_filter = or_(
                    song_filter, Song.artist_normalized.ilike(search_term_normalized)
                )

            song_results_query = (
                db.session.query(
                    Song,
                    func.coalesce(likes_subquery.c.like_count, 0),
                    func.coalesce(listens_subquery.c.listen_count, 0),
                )
                .outerjoin(likes_subquery, Song.id == likes_subquery.c.song_id)
                .outerjoin(listens_subquery, Song.id == listens_subquery.c.song_id)
                .filter(song_filter)
                .order_by(
                    desc(func.coalesce(likes_subquery.c.like_count, 0)), Song.title
                )
                .limit(20 if search_type == "song" else 10)
            )

            liked_song_ids = set()

            current_song_objects = [s for s, _, _ in song_results_query.all()]
            if current_user.is_authenticated and current_song_objects:
                result_song_ids = [s.id for s in current_song_objects]
                if result_song_ids:
                    liked_song_ids = {
                        r[0]
                        for r in db.session.query(likes.c.song_id)
                        .filter(
                            likes.c.user_id == current_user.id,
                            likes.c.song_id.in_(result_song_ids),
                        )
                        .all()
                    }

            for song, like_count, listen_count in song_results_query.all():
                songs_results.append(
                    {
                        "id": song.id,
                        "title": song.title,
                        "artist": song.artist,
                        "file_path": url_for(
                            "static", filename=song.file_path, _external=True
                        ),
                        "cover_art_path": (
                            url_for(
                                "static", filename=song.cover_art_path, _external=True
                            )
                            if song.cover_art_path
                            else url_for(
                                "static",
                                filename="img/covers/default_avatar.png",
                                _external=True,
                            )
                        ),
                        "like_count": like_count,
                        "listen_count": listen_count,
                        "share_count": song.share_count,
                        "is_liked": song.id in liked_song_ids,
                        "duration": song.duration,
                    }
                )

            if search_type == "song":
                return jsonify(
                    {
                        "results_type": "songs",
                        "results": songs_results,
                        "filter_applied": "song",
                    }
                )

        if search_type == "artist" or search_type == "all":
            print(f"[API Search] Searching artists...")

            artist_query = (
                db.session.query(
                    Song.artist, func.min(Song.cover_art_path).label("cover_art_path")
                )
                .filter(Song.artist_normalized.ilike(search_term_normalized))
                .group_by(Song.artist)
                .order_by(Song.artist)
                .limit(10 if search_type == "artist" else 5)
            )
            artists_results = [
                {"name": name, "cover": cover} for name, cover in artist_query.all()
            ]

            if search_type == "artist":
                return jsonify(
                    {
                        "results_type": "artists",
                        "results": artists_results,
                        "filter_applied": "artist",
                    }
                )

        return jsonify(
            {
                "results_type": "mixed",
                "songs": songs_results,
                "artists": artists_results,
                "users": users_results,
                "filter_applied": "all",
            }
        )

    except Exception as e:

        print(f"Error during API search execution: {e}")
        traceback.print_exc()
        return (
            jsonify(
                {
                    "error": "Lỗi server khi tìm kiếm",
                    "results_type": "error",
                    "songs": [],
                    "artists": [],
                    "users": [],
                }
            ),
            500,
        )


@app.route("/api/song/<int:song_id>/comments", methods=["GET", "POST"])
def handle_comments(song_id):
    song = db.session.get(Song, song_id)
    if not song:
        return jsonify({"status": "error", "message": "Bài hát không tồn tại."}), 404

    if request.method == "POST":
        if not current_user.is_authenticated:
            return (
                jsonify(
                    {"status": "error", "message": "Bạn cần đăng nhập để bình luận."}
                ),
                401,
            )

        data = request.get_json()
        if not data or "text" not in data or not data["text"].strip():
            return (
                jsonify(
                    {
                        "status": "error",
                        "message": "Nội dung bình luận không được để trống.",
                    }
                ),
                400,
            )

        comment_text = data["text"].strip()
        try:

            new_comment = Comment(text=comment_text, author=current_user, song=song)
            db.session.add(new_comment)
            db.session.commit()

            author_info = {
                "username": new_comment.author.username,
                "name_display": new_comment.author.name_display,
                "avatar_url": (
                    url_for(
                        "static",
                        filename=new_comment.author.avatar_url,
                        _external=False,
                    )
                    if new_comment.author.avatar_url
                    else url_for(
                        "static",
                        filename="img/avatars/default_avatar.png",
                        _external=False,
                    )
                ),
            }

            print(
                f"--- DEBUG API Comment POST: Author info being sent: {author_info} ---"
            )

            return (
                jsonify(
                    {
                        "status": "success",
                        "message": "Đã gửi bình luận.",
                        "comment": {
                            "id": new_comment.id,
                            "text": new_comment.text,
                            "timestamp": new_comment.timestamp.isoformat(),
                            "author": author_info,
                        },
                    }
                ),
                201,
            )
        except Exception as e:
            db.session.rollback()
            print(f"Error posting comment: {e}")
            import traceback

            traceback.print_exc()
            return (
                jsonify(
                    {"status": "error", "message": "Lỗi máy chủ khi lưu bình luận."}
                ),
                500,
            )

    elif request.method == "GET":

        try:

            comments = song.comments.order_by(desc(Comment.timestamp)).limit(20).all()
            comments_data = [
                {
                    "id": c.id,
                    "text": c.text,
                    "timestamp": c.timestamp.isoformat(),
                    "author": c.author.username if c.author else "Người dùng đã xóa",
                }
                for c in comments
            ]
            return jsonify(comments_data)
        except Exception as e:
            print(f"Error fetching comments: {e}")
            return jsonify({"error": "Lỗi khi tải bình luận"}), 500


@app.route("/api/song/<int:song_id>/like", methods=["POST", "DELETE"])
@login_required
def toggle_like_song(song_id):
    song = Song.query.get_or_404(song_id)
    action_performed = False
    action = "unknown"
    message = ""

    if request.method == "POST":
        if not current_user.has_liked_song(song):
            current_user.like_song(song)
            action_performed = True
            message = "Đã thích bài hát"
            action = "liked"
        else:
            message = "Bạn đã thích bài hát này rồi"
            action = "already_liked"

    elif request.method == "DELETE":
        if current_user.has_liked_song(song):
            current_user.unlike_song(song)
            action_performed = True
            message = "Đã bỏ thích bài hát"
            action = "unliked"
        else:
            message = "Bạn chưa thích bài hát này"
            action = "not_liked"

    if action_performed:
        try:
            db.session.commit()

            like_count = song.like_count
            return (
                jsonify(
                    {
                        "status": "success",
                        "action": action,
                        "message": message,
                        "like_count": like_count,
                    }
                ),
                200,
            )
        except Exception as e:
            db.session.rollback()
            print(f"Error toggling like: {e}")
            return (
                jsonify(
                    {"status": "error", "message": "Lỗi khi cập nhật trạng thái thích."}
                ),
                500,
            )
    else:

        like_count = song.like_count
        return (
            jsonify(
                {
                    "status": "no_change",
                    "action": action,
                    "message": message,
                    "like_count": like_count,
                }
            ),
            200,
        )


@app.route("/api/song/<int:song_id>/share", methods=["POST"])
def increment_share_count(song_id):
    song = Song.query.get_or_404(song_id)
    try:

        song.share_count = func.coalesce(Song.share_count, 0) + 1
        db.session.commit()
        return jsonify({"status": "success", "share_count": song.share_count}), 200
    except Exception as e:
        db.session.rollback()
        print(f"Error incrementing share count: {e}")
        return (
            jsonify({"status": "error", "message": "Lỗi khi cập nhật lượt chia sẻ."}),
            500,
        )


@app.route("/api/song/<int:song_id>/details")
def get_song_details(song_id):

    song = Song.query.get_or_404(song_id)
    is_liked = False
    like_count = 0
    listen_count = 0

    try:

        like_count = song.like_count

        listen_count = (
            db.session.query(func.count(ListeningHistory.id))
            .filter(ListeningHistory.song_id == song.id)
            .scalar()
            or 0
        )

        if current_user.is_authenticated:
            is_liked = current_user.has_liked_song(song)

        details = {
            "id": song.id,
            "title": song.title,
            "artist": song.artist,
            "file_path": url_for("static", filename=song.file_path, _external=True),
            "cover_art_path": (
                url_for("static", filename=song.cover_art_path, _external=True)
                if song.cover_art_path
                else url_for(
                    "static", filename="img/covers/default_cover.png", _external=True
                )
            ),
            "is_liked": is_liked,
            "like_count": like_count,
            "listen_count": listen_count,
            "share_count": song.share_count,
            "duration": song.duration,
            "lyrics": song.lyrics,
        }
        return jsonify(details)
    except Exception as e:
        print(f"Error fetching song details API: {e}")
        return jsonify({"error": "Lỗi khi lấy chi tiết bài hát"}), 500


@app.route("/api/song/<int:song_id>/log_listen", methods=["POST"])
@login_required
def log_listen(song_id):
    song = Song.query.get_or_404(song_id)
    try:

        listen_record = ListeningHistory(listener=current_user, song_played=song)
        db.session.add(listen_record)
        db.session.commit()
        return jsonify({"status": "success", "message": "Listen logged"}), 201
    except Exception as e:
        db.session.rollback()
        print(f"Error logging listening history via API: {e}")
        return jsonify({"status": "error", "message": "Could not log listen"}), 500


@app.route("/api/recommendations")
def api_recommendations():
    limit = request.args.get("limit", 5, type=int)
    all_songs_data = []
    try:

        all_songs_query = Song.query.all()

        if not all_songs_query:
            return jsonify([])

        random.shuffle(all_songs_query)

        songs_to_recommend = all_songs_query[:limit]

        recommended_song_ids = [song.id for song in songs_to_recommend]
        liked_song_ids_by_current_user = set()
        if current_user.is_authenticated and recommended_song_ids:
            liked_song_ids_by_current_user = {
                row[0]
                for row in db.session.query(likes.c.song_id)
                .filter(
                    likes.c.user_id == current_user.id,
                    likes.c.song_id.in_(recommended_song_ids),
                )
                .all()
            }

        for song in songs_to_recommend:
            is_liked = song.id in liked_song_ids_by_current_user
            all_songs_data.append(
                {
                    "id": song.id,
                    "title": song.title,
                    "artist": song.artist,
                    "file_path": url_for(
                        "static", filename=song.file_path, _external=True
                    ),
                    "cover_art_path": (
                        url_for("static", filename=song.cover_art_path, _external=True)
                        if song.cover_art_path
                        else url_for(
                            "static",
                            filename="img/covers/default_cover.png",
                            _external=True,
                        )
                    ),
                    "is_liked": is_liked,
                    "duration": song.duration,
                }
            )

        return jsonify(all_songs_data)

    except Exception as e:
        current_app.logger.error(f"Error generating recommendations: {e}")

        import traceback

        traceback.print_exc()
        return jsonify({"error": "Không thể tạo gợi ý"}), 500


@app.cli.command("init-db")
def init_db_command():
    """Tạo các bảng database."""
    try:
        if not os.path.exists(instance_folder_path):
            os.makedirs(instance_folder_path)
            print(f"Created instance folder at: {instance_folder_path}")
        print("Creating database tables...")

        with app.app_context():
            db.create_all()
            print("Database tables created successfully!")
    except Exception as e:
        print(f"Error creating database tables: {e}")


@app.cli.command("add-sample-data")
def add_sample_data_command():
    """Thêm dữ liệu mẫu vào database."""

    with app.app_context():
        try:
            print("Adding sample data...")

            admin_username = "admin"
            test_user_username = "testuser"
            admin_user = User.query.filter_by(username=admin_username).first()
            if not admin_user:
                admin_user = User(username=admin_username, role="admin")
                admin_user.set_password("password")
                db.session.add(admin_user)
                print(f"Added admin user: {admin_username}")

            user = User.query.filter_by(username=test_user_username).first()
            if not user:
                user = User(username=test_user_username, role="user")
                user.set_password("password")
                db.session.add(user)
                print(f"Added sample user: {test_user_username}")

            sample_song_filename_1 = "sample_song_1.mp3"
            sample_song_rel_path_1 = os.path.join(
                "audio", sample_song_filename_1
            ).replace("\\", "/")
            sample_cover_filename_1 = "cover1_placeholder.png"
            sample_cover_rel_path_1 = os.path.join(
                "img", "covers", sample_cover_filename_1
            ).replace("\\", "/")
            full_file_path_1 = os.path.join(static_folder_path, sample_song_rel_path_1)
            song1 = Song.query.filter_by(file_path=sample_song_rel_path_1).first()
            if not song1 and os.path.exists(full_file_path_1):
                song1 = Song(
                    title="Bài hát mẫu 1",
                    artist="Nghệ sĩ Demo",
                    file_path=sample_song_rel_path_1,
                    cover_art_path=sample_cover_rel_path_1,
                    lyrics="[00:10.50]...",
                    duration=185,
                )
                db.session.add(song1)
                print(f"Added sample song: {song1.title}")
            elif not os.path.exists(full_file_path_1):
                print(f"Warning: Sample file not found at {full_file_path_1}.")

            sample_song_filename_2 = "sample_song_2.mp3"
            sample_song_rel_path_2 = os.path.join(
                "audio", sample_song_filename_2
            ).replace("\\", "/")
            sample_cover_filename_2 = "cover2_placeholder.png"
            sample_cover_rel_path_2 = os.path.join(
                "img", "covers", sample_cover_filename_2
            ).replace("\\", "/")
            full_file_path_2 = os.path.join(static_folder_path, sample_song_rel_path_2)
            song2 = Song.query.filter_by(file_path=sample_song_rel_path_2).first()
            if not song2 and os.path.exists(full_file_path_2):
                song2 = Song(
                    title="Track Demo số 2",
                    artist="Unknown Artist",
                    file_path=sample_song_rel_path_2,
                    cover_art_path=sample_cover_rel_path_2,
                    duration=210,
                )
                db.session.add(song2)
                print(f"Added sample song: {song2.title}")
            elif not os.path.exists(full_file_path_2):
                print(f"Warning: Sample file not found at {full_file_path_2}.")

            db.session.flush()

            if (
                user
                and song1
                and not ListeningHistory.query.filter_by(
                    user_id=user.id, song_id=song1.id
                ).first()
            ):
                db.session.add_all(
                    [
                        ListeningHistory(listener=user, song_played=song1),
                        ListeningHistory(listener=user, song_played=song1),
                    ]
                )
                print(
                    f"Added sample listening history for user '{user.username}' and song '{song1.title}'."
                )
            if (
                user
                and song2
                and not ListeningHistory.query.filter_by(
                    user_id=user.id, song_id=song2.id
                ).first()
            ):
                db.session.add(ListeningHistory(listener=user, song_played=song2))
                print(
                    f"Added sample listening history for user '{user.username}' and song '{song2.title}'."
                )
            if (
                admin_user
                and song1
                and not ListeningHistory.query.filter_by(
                    user_id=admin_user.id, song_id=song1.id
                ).first()
            ):
                db.session.add(ListeningHistory(listener=admin_user, song_played=song1))
                print(
                    f"Added sample listening history for user '{admin_user.username}' and song '{song1.title}'."
                )

            sample_playlist_name = "Playlist Yêu Thích"
            if (
                user
                and not Playlist.query.filter_by(
                    name=sample_playlist_name, user_id=user.id
                ).first()
            ):
                playlist = Playlist(name=sample_playlist_name, owner=user)
                if song1:
                    playlist.songs.append(song1)
                if song2:
                    playlist.songs.append(song2)
                db.session.add(playlist)
                print(
                    f"Added sample playlist '{sample_playlist_name}' for user '{user.username}'."
                )
            elif user:
                print(
                    f"Sample playlist '{sample_playlist_name}' for user '{test_user_username}' already exists or user not found."
                )

            db.session.commit()
            print("Sample data adding process finished.")
        except Exception as e:
            db.session.rollback()
            print(f"Error adding sample data: {e}")


@app.cli.command("boost-song-views")
@click.argument("song_id", type=int)
@click.argument("num_views_to_add", type=int)
def boost_song_views_command(song_id, num_views_to_add):
    """Thêm một số lượt nghe giả cho một bài hát cụ thể."""
    with app.app_context():
        song = db.session.get(Song, song_id)
        if not song:
            print(f"Lỗi: Không tìm thấy bài hát với ID {song_id}.")
            return

        boosting_user = User.query.filter_by(username="admin").first()
        if not boosting_user:

            print(
                "Lỗi: Không tìm thấy user 'admin' để thực hiện boost. Hãy tạo user này hoặc chọn user khác."
            )
            return

        print(
            f"Đang thêm {num_views_to_add} lượt nghe cho bài hát '{song.title}' (ID: {song.id})..."
        )
        for _ in range(num_views_to_add):

            listen_record = ListeningHistory(user_id=boosting_user.id, song_id=song.id)
            db.session.add(listen_record)

        try:
            db.session.commit()
            print(
                f"Hoàn thành! Đã thêm {num_views_to_add} lượt nghe cho '{song.title}'."
            )
        except Exception as e:
            db.session.rollback()
            print(f"Lỗi khi commit lượt nghe: {e}")


@app.cli.command("boost-all-songs-likes-randomly")
@click.option(
    "--min-likes", default=10, help="Số lượt tim tối thiểu cộng thêm cho mỗi bài hát."
)
@click.option(
    "--max-likes", default=200, help="Số lượt tim tối đa cộng thêm cho mỗi bài hát."
)
@click.option(
    "--start-user-id",
    default=20000,
    help='ID bắt đầu cho user "ảo" để like (để tránh trùng user thật và user ảo của boost view).',
)
def boost_all_songs_likes_randomly_command(min_likes, max_likes, start_user_id):
    """Thêm một số lượt thích (tim) giả ngẫu nhiên cho TẤT CẢ các bài hát."""
    with app.app_context():
        songs = Song.query.all()
        if not songs:
            print("Không có bài hát nào trong hệ thống để boost likes.")
            return

        print(
            f"Đang thêm lượt thích ngẫu nhiên (từ {min_likes} đến {max_likes}) cho tất cả các bài hát..."
        )

        total_new_likes_committed = 0

        for song in songs:
            num_likes_to_add_for_this_song = random.randint(min_likes, max_likes)
            actual_likes_added_for_this_song = 0

            print(
                f" - Chuẩn bị thêm {num_likes_to_add_for_this_song} lượt thích cho '{song.title}' (ID: {song.id})..."
            )

            for i in range(num_likes_to_add_for_this_song):

                virtual_user_id_for_this_like = (
                    start_user_id + total_new_likes_committed + i
                )

                virtual_user = db.session.get(User, virtual_user_id_for_this_like)
                if not virtual_user:
                    virtual_username = f"_boost_liker_{virtual_user_id_for_this_like}"

                    if User.query.filter_by(username=virtual_username).first():
                        print(
                            f"Cảnh báo: Username {virtual_username} đã tồn tại, đang thử ID user tiếp theo."
                        )

                        continue

                    virtual_user = User(
                        id=virtual_user_id_for_this_like,
                        username=virtual_username,
                        role="system_liker",
                    )
                    virtual_user.set_password(os.urandom(16))
                    db.session.add(virtual_user)
                    try:

                        db.session.flush()
                    except Exception as e_user_flush:
                        db.session.rollback()
                        print(
                            f"Lỗi khi flush user ảo {virtual_username}: {e_user_flush}. Bỏ qua lượt like này."
                        )
                        continue

                already_liked = (
                    db.session.query(likes)
                    .filter_by(user_id=virtual_user.id, song_id=song.id)
                    .first()
                )
                if not already_liked:
                    try:
                        insert_like = likes.insert().values(
                            user_id=virtual_user.id, song_id=song.id
                        )
                        db.session.execute(insert_like)
                        actual_likes_added_for_this_song += 1
                    except Exception as e_like:

                        print(
                            f"Lỗi khi thêm like từ user ID {virtual_user.id} cho song ID {song.id}: {e_like}. Bỏ qua."
                        )
                        continue

            total_new_likes_committed += actual_likes_added_for_this_song
            if actual_likes_added_for_this_song > 0:
                print(
                    f"   -> Đã thêm thành công {actual_likes_added_for_this_song} lượt thích mới cho '{song.title}'."
                )

        try:
            db.session.commit()
            print(
                f"Hoàn thành! Tổng cộng đã thêm {total_new_likes_committed} lượt thích mới cho các bài hát."
            )
        except Exception as e:
            db.session.rollback()
            print(f"Lỗi nghiêm trọng khi commit cuối cùng tất cả lượt thích: {e}")


@app.cli.command("boost-all-songs-randomly")
@click.option(
    "--min-views", default=50, help="Số lượt xem tối thiểu cộng thêm cho mỗi bài hát."
)
@click.option(
    "--max-views", default=500, help="Số lượt xem tối đa cộng thêm cho mỗi bài hát."
)
def boost_all_songs_randomly_command(min_views, max_views):
    """Thêm một số lượt nghe giả ngẫu nhiên cho TẤT CẢ các bài hát."""
    with app.app_context():
        songs = Song.query.all()
        if not songs:
            print("Không có bài hát nào trong hệ thống để boost.")
            return

        boosting_user = User.query.filter_by(username="admin").first()
        if not boosting_user:
            print(
                "Lỗi: Không tìm thấy user 'admin' để thực hiện boost. Hãy tạo user này hoặc chọn user khác."
            )
            return

        print(
            f"Đang thêm lượt nghe ngẫu nhiên (từ {min_views} đến {max_views}) cho tất cả các bài hát..."
        )
        for song in songs:
            num_views_to_add = random.randint(min_views, max_views)
            for _ in range(num_views_to_add):
                listen_record = ListeningHistory(
                    user_id=boosting_user.id, song_id=song.id
                )
                db.session.add(listen_record)
            print(
                f" - Đã thêm {num_views_to_add} lượt nghe cho '{song.title}' (ID: {song.id})"
            )

        try:
            db.session.commit()
            print("Hoàn thành tăng lượt xem ngẫu nhiên cho tất cả bài hát!")
        except Exception as e:
            db.session.rollback()
            print(f"Lỗi khi commit lượt nghe cho tất cả bài hát: {e}")

@app.route('/api/playlists/featured')
def api_featured_playlists():
    try:
        featured_playlists = Playlist.query.filter_by(is_public=True, is_featured=True)\
                                           .order_by(desc(Playlist.created_at)).limit(6).all()
        
        playlists_data = []
        for pl in featured_playlists:
            cover_url = url_for('static', filename='img/covers/default_empty_playlist.png', _external=True)
            
            if pl.custom_cover_path:
                clean_path = pl.custom_cover_path.lstrip('/')
                cover_url = url_for('static', filename=clean_path, _external=True)
            else:
                first_song = pl.songs.first()
                if first_song and first_song.cover_art_path:
                    clean_path = first_song.cover_art_path.lstrip('/')
                    cover_url = url_for('static', filename=clean_path, _external=True)

            playlists_data.append({
                "id": pl.id,
                "name": pl.name,
                "custom_cover_path": cover_url, 
                "owner": pl.owner.name_display if pl.owner else "Unknown"
            })
            
        return jsonify(playlists_data)
    except Exception as e:
        print(f"Lỗi API featured playlists: {e}")
        return jsonify({"error": "Không thể lấy danh sách playlist"}), 500

from sqlalchemy import or_

from sqlalchemy import or_

@app.route('/api/playlists/user/<username>', methods=['GET'])
def get_user_playlists(username):
    try:
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"success": False, "message": "Không tìm thấy người dùng"}), 404

        playlists = Playlist.query.filter(
            or_(Playlist.user_id == user.id, Playlist.partner_id == user.id)
        ).order_by(Playlist.created_at.desc()).all()

        result = []
        for p in playlists:
            owner = User.query.get(p.user_id)
            partner = User.query.get(p.partner_id) if p.partner_id else None
            
            def get_avatar_url(u):
                if u and getattr(u, 'avatar_url', None):
                    clean_path = u.avatar_url.lstrip('/')
                    return url_for('static', filename=clean_path, _external=True)
                return url_for('static', filename='img/avatars/default_avatar.png', _external=True)

            owner_avatar_url = get_avatar_url(owner) if owner else ""
            partner_avatar_url = get_avatar_url(partner) if partner else ""

            song_count = p.songs.count()
            all_songs = p.songs.all()
            
            is_custom = bool(p.custom_cover_path)
            playlist_cover = ""
            collage_covers = []

            song_count = p.songs.count()
            all_songs = p.songs.all()
            
            is_custom = bool(p.custom_cover_path)
            playlist_cover = ""
            collage_covers = []
            
            if is_custom:
                clean_path = p.custom_cover_path.lstrip('/')
                playlist_cover = url_for('static', filename=clean_path, _external=True)
            else:
                if song_count >= 4:
                    for s in all_songs[:4]:
                        if s.cover_art_path:
                            clean_path = s.cover_art_path.lstrip('/')
                            collage_covers.append(url_for('static', filename=clean_path, _external=True))
                        else:
                            collage_covers.append(url_for('static', filename='img/covers/default_cover.png', _external=True))
                elif song_count > 0:
                    if all_songs[0].cover_art_path:
                        clean_path = all_songs[0].cover_art_path.lstrip('/')
                        playlist_cover = url_for('static', filename=clean_path, _external=True)
                    else:
                        playlist_cover = url_for('static', filename='img/covers/default_cover.png', _external=True)
                else:
                    playlist_cover = url_for('static', filename='img/covers/default_cover.png', _external=True)

            updated_at_stable = p.id + song_count

            playlist_data = {
                "id": p.id,
                "name": p.name,
                "description": p.description or "",
                "plays": 0,
                "likes": 0,
                "cover_url": playlist_cover, 
                "is_custom_cover": is_custom,
                "song_count": song_count,
                "collage_covers": collage_covers,
                "updated_at": updated_at_stable,
                "is_blend": getattr(p, 'is_blend', False),
                "owner_username": owner.username if owner else "",
                "owner_avatar": owner_avatar_url,
                "partner_username": partner.username if partner else "",
                "partner_avatar": partner_avatar_url
            }
            result.append(playlist_data)

        return jsonify(result), 200

    except Exception as e:
        print(f"Lỗi API get_user_playlists: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    
@app.route('/api/songs/search', methods=['GET'])
def search_songs():
    query = request.args.get('q', '').lower()
    if not query:
        return jsonify([])
    
    songs = Song.query.filter((Song.title.ilike(f'%{query}%')) | (Song.artist.ilike(f'%{query}%'))).all()
    
    songs_data = []
    for song in songs:
        songs_data.append({
            "id": song.id,
            "title": song.title,
            "artist": song.artist,
            "duration": song.duration,
            "cover_art_path": url_for('static', filename=song.cover_art_path, _external=True) if song.cover_art_path else None,
            "file_path": url_for('static', filename=song.file_path, _external=True) if song.file_path else None
        })
    return jsonify(songs_data)

@app.route('/api/register', methods=['POST'])
def api_register():
    try:
        data = request.json
        name = data.get('name')
        username = data.get('username')
        password = data.get('password')
        
        if User.query.filter_by(username=username).first():
            return jsonify({"success": False, "message": "Tên đăng nhập đã tồn tại!"}), 400
            
        new_user = User(display_name=name, username=username)
        
        new_user.set_password(password)
        
        db.session.add(new_user)
        db.session.commit()
        return jsonify({"success": True, "message": "Đăng ký thành công!"})
    except Exception as e:
        print(f"Lỗi đăng ký: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500

@app.route('/api/login', methods=['POST'])
def api_login():
    try:
        data = request.json
        username = data.get('username')
        password = data.get('password')
        
        user = User.query.filter_by(username=username).first()
        
        if user and user.check_password(password):
            avatar_full_url = ""
            if user.avatar_url:
                avatar_full_url = url_for('static', filename=user.avatar_url, _external=True)
                
            return jsonify({
                "success": True, 
                "message": "Đăng nhập thành công!", 
                "display_name": user.name_display, 
                "avatar_url": avatar_full_url  
            })
            
        return jsonify({"success": False, "message": "Sai tài khoản hoặc mật khẩu!"}), 401
    except Exception as e:
        print(f"Lỗi đăng nhập: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    
@app.route('/api/favorite/toggle', methods=['POST'])
def toggle_favorite():
    try:
        data = request.json
        username = data.get('username')
        song_id = data.get('song_id')

        user = User.query.filter_by(username=username).first()
        song = Song.query.get(song_id)

        if not user or not song:
            return jsonify({"success": False, "message": "Lỗi dữ liệu"}), 400

        if user.has_liked_song(song):
            user.unlike_song(song)
            is_liked = False
            message = "Đã bỏ yêu thích!"
        else:
            user.like_song(song)
            is_liked = True
            message = "Đã thêm vào yêu thích!"
            
        db.session.commit()
        
        return jsonify({"success": True, "is_liked": is_liked, "message": message})
    except Exception as e:
        print(f"Lỗi thả tim: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500

@app.route('/api/user/playlists/create', methods=['POST'])
def create_mobile_playlist():
    try:
        username = request.form.get('username')
        name = request.form.get('name')
        description = request.form.get('description', '')
        is_public_str = request.form.get('is_public', 'true').lower()
        is_public = is_public_str == 'true'
        
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"success": False, "message": "User không tồn tại"}), 404
            
        new_pl = Playlist(name=name, description=description, user_id=user.id, is_public=is_public)
        db.session.add(new_pl)
        db.session.flush() 

        if 'cover_image' in request.files:
            cover_file = request.files['cover_image']
            if cover_file and cover_file.filename != '':
                uploaded_cover_path = save_playlist_cover(cover_file, new_pl)
                if uploaded_cover_path:
                    new_pl.custom_cover_path = uploaded_cover_path

        db.session.commit()
        return jsonify({
            "success": True, 
            "message": "Đã tạo Playlist mới!",
            "playlist_id": new_pl.id
        })
    except Exception as e:
        db.session.rollback()
        print(f"Lỗi tạo playlist (Mobile API): {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    try:
        data = request.json
        username = data.get('username')
        name = data.get('name')
        
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"success": False, "message": "User không tồn tại"})
            
        new_pl = Playlist(name=name, user_id=user.id, is_public=False)
        db.session.add(new_pl)
        db.session.commit()
        return jsonify({"success": True, "message": "Đã tạo Playlist mới!"})
    except Exception as e:
        print(f"Lỗi tạo playlist: {e}")
        return jsonify({"success": False, "message": "Lỗi server"})

@app.route('/api/user/playlists/add_song', methods=['POST'])
def add_song_to_playlist():
    try:
        data = request.json
        playlist_id = data.get('playlist_id')
        song_id = data.get('song_id')
        
        playlist = Playlist.query.get(playlist_id)
        song = Song.query.get(song_id)
        
        if not playlist or not song:
            return jsonify({"success": False, "message": "Lỗi dữ liệu"})
            
        if song not in playlist.songs:
            playlist.songs.append(song)
            db.session.commit()
            return jsonify({"success": True, "message": "Đã thêm vào Playlist!"})
        else:
            return jsonify({"success": False, "message": "Bài hát đã có trong Playlist này!"})
    except Exception as e:
        print(f"Lỗi thêm bài hát: {e}")
        return jsonify({"success": False, "message": "Lỗi Database (Kiểm tra lại Model)"})
    
@app.route('/api/songs/<int:song_id>/lyrics', methods=['GET'])
def get_mobile_lyrics(song_id):
    try:
        song = Song.query.get(song_id)
        if not song:
            return jsonify({"success": False, "lyrics": "Không tìm thấy bài hát."})

        fallback_lyrics = f"🎵 Đang phát: {song.title} 🎵\n\n(Chưa có lời bài hát trong hệ thống)"
        
        lyrics_text = getattr(song, 'lyrics', fallback_lyrics)
        if not lyrics_text:
            lyrics_text = fallback_lyrics

        return jsonify({"success": True, "lyrics": lyrics_text})
    except Exception as e:
        print(f"Lỗi lấy lời bài hát: {e}")
        return jsonify({"success": False, "lyrics": "Lỗi hệ thống khi tải lời bài hát."})

@app.route('/api/playlists/<int:playlist_id>/songs', methods=['GET'])
def get_songs_in_playlist(playlist_id):
    try:
        playlist = Playlist.query.get(playlist_id)
        if not playlist:
            return jsonify({"error": "Không tìm thấy playlist"}), 404
            
        songs_data = []
        for song in playlist.songs:
            songs_data.append({
                "id": song.id,
                "title": song.title,
                "artist": song.artist,
                "duration": song.duration,
                "cover_art_path": url_for('static', filename=song.cover_art_path, _external=True) if song.cover_art_path else None,
                "file_path": url_for('static', filename=song.file_path, _external=True) if song.file_path else None
            })
            
        return jsonify(songs_data), 200
        
    except Exception as e:
        print(f"Lỗi API get_songs_in_playlist: {e}")
        return jsonify({"error": "Lỗi server khi lấy bài hát"}), 500
    
    try:
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify([])
        
        playlists = Playlist.query.filter_by(user_id=user.id).all()
        playlists_data = []
        
        for pl in playlists:
            cover_url = None
            if pl.custom_cover_path:
                cover_url = url_for('static', filename=pl.custom_cover_path, _external=True)
            else:
                first_song = pl.songs.first()
                if first_song and first_song.cover_art_path:
                    cover_url = url_for('static', filename=first_song.cover_art_path, _external=True)

            playlists_data.append({
                "id": pl.id,
                "name": pl.name,
                "cover_url": cover_url,
                "plays": random.randint(100, 5000), 
                "likes": random.randint(10, 500)    
            })
            
        return jsonify(playlists_data), 200
        
    except Exception as e:
        print(f"Lỗi API lấy playlist của user {username}: {e}")
        return jsonify({"error": "Lỗi server khi tải thư viện"}), 500
    
@app.route('/api/history', methods=['POST'])
def add_listening_history():
    data = request.json
    username = data.get('username')
    song_id = data.get('song_id')
    
    if not username or not song_id:
        return jsonify({"error": "Thiếu dữ liệu"}), 400
        
    user = User.query.filter_by(username=username).first()
    if not user:
        return jsonify({"error": "Không tìm thấy user"}), 404
        
    last_history = ListeningHistory.query.filter_by(user_id=user.id).order_by(desc(ListeningHistory.timestamp)).first()
    if last_history and last_history.song_id == song_id:
        return jsonify({"message": "Bài hát đang nghe, không lặp lại lịch sử"}), 200

    new_history = ListeningHistory(user_id=user.id, song_id=song_id)
    db.session.add(new_history)
    db.session.commit()
    
    return jsonify({"message": "Đã lưu lịch sử nghe nhạc!"}), 200

@app.route('/api/history/<username>', methods=['GET'])
def get_mobile_listening_history(username):
    try:
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"error": "Không tìm thấy user"}), 404
            
        latest_listen_subquery = (
            db.session.query(
                ListeningHistory.song_id,
                func.max(ListeningHistory.timestamp).label("last_played")
            )
            .filter(ListeningHistory.user_id == user.id)
            .group_by(ListeningHistory.song_id)
            .subquery()
        )

        recent_listens_query = (
            db.session.query(Song, latest_listen_subquery.c.last_played)
            .join(latest_listen_subquery, Song.id == latest_listen_subquery.c.song_id)
            .order_by(desc(latest_listen_subquery.c.last_played))
            .limit(20) 
        )

        history_data = []
        for song, last_played in recent_listens_query.all():
            history_data.append({
                "id": song.id,
                "title": song.title,
                "artist": song.artist,
                "duration": song.duration,
                "cover_art_path": url_for('static', filename=song.cover_art_path, _external=True) if song.cover_art_path else None,
                "file_path": url_for('static', filename=song.file_path, _external=True) if song.file_path else None,
                "played_at": last_played.isoformat()
            })
                
        return jsonify(history_data), 200
        
    except Exception as e:
        print(f"Lỗi API get_mobile_listening_history: {e}")
        traceback.print_exc()
        return jsonify({"error": "Lỗi server khi lấy lịch sử"}), 500
    
@app.route('/api/playlists/user/<username>/artists', methods=['GET'])
def get_user_artists_mobile(username):
    try:
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify([])
        
        playlists = Playlist.query.filter_by(user_id=user.id).all()
        
        artists_set = set()
        artists_data = []
        
        for pl in playlists:
            for song in pl.songs:
                if song.artist and song.artist not in artists_set:
                    artists_set.add(song.artist)
                    
                    artists_data.append({
                        "id": song.id,
                        "title": song.title,
                        "artist": song.artist,
                        "file_path": url_for('static', filename=song.file_path, _external=True) if song.file_path else None,
                        "cover_art_path": url_for('static', filename=song.cover_art_path, _external=True) if song.cover_art_path else None,
                        "duration": song.duration
                    })
                    
        return jsonify(artists_data), 200
        
    except Exception as e:
        print(f"Lỗi API lấy nghệ sĩ của user {username}: {e}")
        return jsonify({"error": "Lỗi server"}), 500
    
@app.route('/api/favorites', methods=['GET'])
def get_favorite_songs():
    try:
        username = request.args.get('username')
        print(f">>> [API TIM] Đang tải danh sách tim của user: {username}") 
        
        if not username:
            return jsonify({"error": "Thiếu username"}), 400
            
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"error": "Không tìm thấy user"}), 404
            
        favorite_songs = db.session.query(Song).join(likes, Song.id == likes.c.song_id).filter(likes.c.user_id == user.id).all()
        print(f">>> [API TIM] Đã tìm thấy {len(favorite_songs)} bài hát!") 
        
        songs_data = []
        for song in favorite_songs:
            songs_data.append({
                "id": song.id,
                "title": song.title,
                "artist": song.artist,
                "duration": song.duration,
                "cover_art_path": url_for('static', filename=song.cover_art_path, _external=True) if song.cover_art_path else None,
                "file_path": url_for('static', filename=song.file_path, _external=True) if song.file_path else None
            })
            
        return jsonify(songs_data), 200
        
    except Exception as e:
        print(f"Lỗi API get_favorite_songs: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": "Lỗi server khi lấy bài hát yêu thích"}), 500

@app.route('/api/playlists/<int:playlist_id>', methods=['DELETE'])
def api_delete_playlist(playlist_id):
    try:
        playlist = Playlist.query.get(playlist_id)
        if not playlist:
            return jsonify({"success": False, "message": "Không tìm thấy playlist"}), 404
        db.session.delete(playlist)
        db.session.commit()
        return jsonify({"success": True, "message": "Đã xóa playlist"})
    except Exception as e:
        db.session.rollback()
        return jsonify({"success": False, "message": "Lỗi server"}), 500

@app.route('/api/playlists/<int:playlist_id>/update', methods=['POST'])
def api_update_playlist(playlist_id):
    try:
        playlist = Playlist.query.get(playlist_id)
        if not playlist:
            return jsonify({"success": False, "message": "Không tìm thấy playlist"}), 404
            
        new_name = request.form.get('name')
        new_description = request.form.get('description')
        is_public_str = request.form.get('is_public')

        if new_name:
            playlist.name = new_name
        if new_description is not None:
            playlist.description = new_description
        if is_public_str:
            playlist.is_public = is_public_str.lower() == 'true'

        new_cover_url = None 

        if 'cover_image' in request.files:
            cover_file = request.files['cover_image']
            if cover_file and cover_file.filename != '':
                uploaded_cover_path = save_playlist_cover(cover_file, playlist)
                if uploaded_cover_path:
                    playlist.custom_cover_path = uploaded_cover_path
                    new_cover_url = url_for('static', filename=uploaded_cover_path, _external=True)
                    
        db.session.commit()
        return jsonify({
            "success": True, 
            "message": "Đã cập nhật Playlist!",
            "new_cover_url": new_cover_url 
        })
    except Exception as e:
        db.session.rollback()
        print(f"Lỗi update playlist (Mobile API): {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    try:
        playlist = Playlist.query.get(playlist_id)
        if not playlist:
            return jsonify({"success": False, "message": "Không tìm thấy playlist"}), 404
            
        new_name = request.form.get('name')
        new_description = request.form.get('description')
        is_public_str = request.form.get('is_public')

        if new_name:
            playlist.name = new_name
        if new_description is not None: 
            playlist.description = new_description
        if is_public_str:
            playlist.is_public = is_public_str.lower() == 'true'

        if 'cover_image' in request.files:
            cover_file = request.files['cover_image']
            if cover_file and cover_file.filename != '':
                uploaded_cover_path = save_playlist_cover(cover_file, playlist)
                if uploaded_cover_path:
                    playlist.custom_cover_path = uploaded_cover_path
                    
        db.session.commit()
        return jsonify({"success": True, "message": "Đã cập nhật Playlist!"})
    except Exception as e:
        db.session.rollback()
        print(f"Lỗi update playlist (Mobile API): {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    try:
        data = request.json
        new_name = data.get('name')
        playlist = Playlist.query.get(playlist_id)
        if not playlist:
            return jsonify({"success": False, "message": "Không tìm thấy playlist"}), 404
            
        if new_name:
            playlist.name = new_name
            
        db.session.commit()
        return jsonify({"success": True, "message": "Đã cập nhật tên Playlist!"})
    except Exception as e:
        db.session.rollback()
        return jsonify({"success": False, "message": "Lỗi server"}), 500

@app.route('/api/playlists/<int:playlist_id>/songs/<int:song_id>', methods=['DELETE'])
def api_remove_song_from_playlist(playlist_id, song_id):
    try:
        sql = text("DELETE FROM playlist_songs WHERE playlist_id = :p_id AND song_id = :s_id")
        db.session.execute(sql, {"p_id": playlist_id, "s_id": song_id})
        db.session.commit()
        
        return jsonify({"success": True, "message": "Đã xóa bài hát khỏi playlist"})
    except Exception as e:
        db.session.rollback()
        print(f"Lỗi xóa bài: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    
@app.route('/api/feed', methods=['GET'])
def get_mobile_feed():
    try:
        posts = CommunityPost.query.order_by(desc(CommunityPost.created_at)).limit(30).all()

        feed_data = []
        for post in posts:
            song = post.song
            listener = post.user

            like_count = song.like_count
            comment_count = Comment.query.filter_by(song_id=song.id).count()

            feed_data.append({
                "id": str(post.id),
                "username": listener.username,
                "avatarUrl": url_for('static', filename=listener.avatar_url, _external=True) if listener.avatar_url else url_for('static', filename='img/avatars/default_avatar.png', _external=True),
                "coverUrl": url_for('static', filename=song.cover_art_path, _external=True) if song.cover_art_path else None,
                "caption": post.caption,
                "timeAgo": post.created_at.isoformat(),
                "likeCount": like_count,
                "commentCount": comment_count
            })

        if len(feed_data) < 5:
            import random
            recent_listens = db.session.query(ListeningHistory, Song, User)\
                .join(Song, ListeningHistory.song_id == Song.id)\
                .join(User, ListeningHistory.user_id == User.id)\
                .order_by(desc(ListeningHistory.timestamp))\
                .limit(15).all()

            for listen, song, listener in recent_listens:
                like_count = db.session.query(func.count(likes.c.user_id)).filter(likes.c.song_id == song.id).scalar() or 0
                if like_count == 0: like_count = random.randint(10, 500)

                comment_count = Comment.query.filter_by(song_id=song.id).count()
                if comment_count == 0: comment_count = random.randint(5, 150)

                captions = [
                    f"Đang phiêu cùng: {song.title} - {song.artist}",
                    f"Nhạc suy quá anh em ơi, nghe lúc trời mưa đúng đỉnh! 🌧️",
                    f"Giai điệu này làm mình nhớ lại nhiều thứ... 🎧",
                    f"Cực kỳ recommend bài này cho mọi người nhé! 🔥",
                    f"Repeat bài '{song.title}' cả ngày hôm nay rồi 🎶"
                ]

                feed_data.append({
                    "id": str(listen.id) + "_history",
                    "username": listener.username,
                    "avatarUrl": url_for('static', filename=listener.avatar_url, _external=True) if listener.avatar_url else url_for('static', filename='img/avatars/default_avatar.png', _external=True),
                    "coverUrl": url_for('static', filename=song.cover_art_path, _external=True) if song.cover_art_path else None,
                    "caption": random.choice(captions),
                    "timeAgo": listen.timestamp.isoformat(),
                    "likeCount": like_count,
                    "commentCount": comment_count
                })

        return jsonify(feed_data), 200
    except Exception as e:
        print(f"Lỗi API get_mobile_feed: {e}")
        return jsonify({"error": "Lỗi server"}), 500
    
@app.route("/api/stories/feed", methods=["GET"])
def get_stories_feed():
    username = request.args.get("username")
    if not username:
        return jsonify({"error": "Missing username"}), 400

    current_user = User.query.filter_by(username=username).first()
    if not current_user:
        return jsonify({"error": "User not found"}), 404

    twenty_four_hours_ago = datetime.utcnow() - timedelta(days=1)

    followed_ids_query = db.session.query(followers.c.followed_id).filter(
        followers.c.follower_id == current_user.id
    ).all()
    followed_ids = [row[0] for row in followed_ids_query]
    
    followed_ids.append(current_user.id)

    users_with_active_stories = User.query.join(Story).filter(
        User.id.in_(followed_ids),
        Story.created_at >= twenty_four_hours_ago
    ).distinct().all()

    feed_data = []
    my_story_data = None
    
    for u in users_with_active_stories:
        user_data = {
            "user_id": u.id,
            "username": u.username,
            "display_name": u.display_name or u.username,
            "avatar_url": url_for('static', filename=u.avatar_url, _external=True) if u.avatar_url else "default_avatar"
        }
        
        if u.id == current_user.id:
            my_story_data = user_data
        else:
            feed_data.append(user_data)

    if my_story_data:
        feed_data.insert(0, my_story_data)

    return jsonify(feed_data), 200
    username = request.args.get("username")
    if not username:
        return jsonify({"error": "Missing username"}), 400

    current_user = User.query.filter_by(username=username).first()
    if not current_user:
        return jsonify({"error": "User not found"}), 404

    twenty_four_hours_ago = datetime.utcnow() - timedelta(days=1)

    followed_ids = db.session.query(followers.c.followed_id).filter(
        followers.c.follower_id == current_user.id
    ).subquery()

    users_with_active_stories = User.query.join(Story).filter(
        User.id.in_(followed_ids),
        Story.created_at >= twenty_four_hours_ago
    ).distinct().all()

    feed_data = []
    for u in users_with_active_stories:
        feed_data.append({
            "user_id": u.id,
            "username": u.username,
            "display_name": getattr(u, 'name', u.username), 
            "avatar_url": getattr(u, 'avatar', "default_avatar")
        })

    return jsonify(feed_data), 200

@app.route("/api/stories/<int:user_id>", methods=["GET"])
def get_user_stories(user_id):
    twenty_four_hours_ago = datetime.utcnow() - timedelta(days=1)

    stories = Story.query.filter(
        Story.user_id == user_id,
        Story.created_at >= twenty_four_hours_ago
    ).order_by(Story.created_at.asc()).all()

    stories_data = []
    for story in stories:
        stories_data.append({
            "id": story.id,
            "media_url": story.media_url,
            "created_at": story.created_at.strftime("%Y-%m-%d %H:%M:%S") 
        })

    return jsonify(stories_data), 200
    
import re
from flask import Response, send_file

@app.route('/static/audio/<path:filename>')
def stream_audio_custom(filename):
    """
    Ghi đè đường dẫn static mặc định để chia nhỏ file MP3 (Streaming),
    tránh lỗi 'unexpected end of stream' trên Android.
    """
    full_path = os.path.join(app.config['STATIC_FOLDER'], 'audio', filename)
    if not os.path.exists(full_path):
        return abort(404)

    file_size = os.path.getsize(full_path)
    range_header = request.headers.get('Range', None)

    if not range_header:
        return send_file(full_path, mimetype="audio/mpeg", conditional=True)

    byte1, byte2 = 0, None
    match = re.search(r'bytes=(\d+)-(.*)', range_header)
    if match:
        groups = match.groups()
        if groups[0]: byte1 = int(groups[0])
        if groups[1]: byte2 = int(groups[1])

    if byte2 is None:
        byte2 = file_size - 1

    length = byte2 - byte1 + 1

    def generate():
        with open(full_path, 'rb') as f:
            f.seek(byte1)
            remaining = length
            chunk_size = 1024 * 64
            while remaining > 0:
                data = f.read(min(chunk_size, remaining))
                if not data:
                    break
                remaining -= len(data)
                yield data

    rv = Response(generate(), 206, mimetype='audio/mpeg', direct_passthrough=True)
    rv.headers.add('Content-Range', f'bytes {byte1}-{byte2}/{file_size}')
    rv.headers.add('Accept-Ranges', 'bytes')
    rv.headers.add('Content-Length', str(length))
    return rv

@app.route("/api/v2/song/<int:song_id>/comments", methods=["GET"])
def get_comments_v2(song_id):
    """Lấy danh sách bình luận kèm thông tin Avatar và Tên hiển thị"""
    song = Song.query.get_or_404(song_id)
    comments = Comment.query.filter_by(song_id=song_id)\
                      .order_by(Comment.timestamp.desc()).limit(50).all()
    
    output = []
    for c in comments:
        output.append({
            "id": c.id,
            "text": c.text,
            "timestamp": c.timestamp.isoformat(),
            "user": {
                "username": c.author.username,
                "display_name": c.author.name_display,
                "avatar_url": url_for('static', filename=c.author.avatar_url, _external=True) 
                              if c.author.avatar_url else url_for('static', filename='img/avatars/default_avatar.png', _external=True)
            }
        })
    return jsonify(output)

@app.route("/api/v2/song/comments/add", methods=["POST"])
def add_comment_v2():
    """API gửi bình luận mới từ Mobile"""
    data = request.json
    username = data.get('username')
    song_id = data.get('song_id')
    content = data.get('text')

    user = User.query.filter_by(username=username).first()
    if not user or not content:
        return jsonify({"success": False, "message": "Dữ liệu không hợp lệ"}), 400

    new_comment = Comment(text=content, user_id=user.id, song_id=song_id)
    db.session.add(new_comment)
    db.session.commit()
    
    return jsonify({
        "success": True, 
        "message": "Đã gửi bình luận!",
        "comment_id": new_comment.id
    })
    
@app.route("/api/artist/<path:artist_name>", methods=["GET"])
def api_artist_detail(artist_name):
    try:
        viewer_username = request.args.get('viewer')
        
        artist_name_decoded = unquote(artist_name, encoding="utf-8")
        normalized_artist_search_name = normalize_text(artist_name_decoded)

        artist_obj = Artist.query.filter(
            Artist.name_normalized == normalized_artist_search_name
        ).first()

        artist_data = {
            "name": artist_name_decoded,
            "bio": "Thông tin giới thiệu về nghệ sĩ này hiện chưa có.",
            "profile_image_url": url_for('static', filename='img/avatars/default_avatar.png', _external=True),
            "banner_image_url": None,
            "followers_count": 0,
            "is_following": False 
        }

        if artist_obj:
            artist_data["name"] = artist_obj.name
            if artist_obj.bio:
                artist_data["bio"] = artist_obj.bio
            if artist_obj.profile_image_path:
                artist_data["profile_image_url"] = url_for('static', filename=artist_obj.profile_image_path, _external=True)
            if artist_obj.banner_image_path:
                artist_data["banner_image_url"] = url_for('static', filename=artist_obj.banner_image_path, _external=True)
            
            artist_data["followers_count"] = artist_obj.followers.count()
            
            if viewer_username:
                viewer = User.query.filter_by(username=viewer_username).first()
                if viewer and viewer in artist_obj.followers:
                    artist_data["is_following"] = True

        songs_by_artist = Song.query.filter(
            func.lower(Song.artist_normalized) == func.lower(normalize_text(artist_data["name"]))
        ).order_by(Song.title).all()

        songs_data = []
        for song in songs_by_artist:
            songs_data.append({
                "id": song.id,
                "title": song.title,
                "artist": song.artist,
                "file_path": url_for("static", filename=song.file_path, _external=True),
                "cover_art_path": url_for("static", filename=song.cover_art_path, _external=True) if song.cover_art_path else url_for("static", filename="img/covers/default_cover.png", _external=True),
                "duration": song.duration,
                "like_count": song.like_count,
                "share_count": song.share_count
            })

        if artist_data["profile_image_url"].endswith("default_avatar.png") and len(songs_data) > 0:
            artist_data["profile_image_url"] = songs_data[0]["cover_art_path"]
            artist_data["banner_image_url"] = songs_data[0]["cover_art_path"] 

        return jsonify({
            "success": True,
            "artist": artist_data,
            "songs": songs_data
        })

    except Exception as e:
        print(f"Lỗi API lấy chi tiết nghệ sĩ: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    
@app.route('/api/artists/unique_story', methods=['GET'])
def get_unique_story_artists():
    limit = request.args.get('limit', 10, type=int)
    try:
        unique_artists = db.session.query(distinct(Song.artist))\
            .filter(Song.artist.isnot(None), Song.artist != "")\
            .order_by(func.random())\
            .limit(limit)\
            .all()

        artists_data = []
        for artist_tuple in unique_artists:
            artist_name = artist_tuple[0]

            song = Song.query.filter_by(artist=artist_name)\
                .filter(Song.cover_art_path.isnot(None), Song.cover_art_path != "")\
                .first()

            if not song:
                song = Song.query.filter_by(artist=artist_name).first()

            if song:
                artists_data.append({
                    "id": song.id,
                    "title": song.title,
                    "artist": song.artist,
                    "file_path": url_for("static", filename=song.file_path, _external=True),
                    "cover_art_path": url_for("static", filename=song.cover_art_path, _external=True) if song.cover_art_path else None,
                    "duration": song.duration,
                })

        return jsonify(artists_data), 200
    except Exception as e:
        print(f"Lỗi API unique artists: {e}")
        return jsonify({"error": "Lỗi server"}), 500
    
@app.route('/api/user/profile/<username>', methods=['GET'])
def get_user_profile_mobile(username):
    """API lấy thông tin profile để hiển thị trên App"""
    viewer_username = request.args.get('viewer')
    try:
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"error": "User không tồn tại"}), 404

        is_following = False
        if viewer_username:
            viewer = User.query.filter_by(username=viewer_username).first()
            if viewer:
                is_following = viewer.is_following(user)

        total_following = user.followed.count() + user.followed_artists.count()

        return jsonify({
            "id": user.id,
            "username": user.username,
            "display_name": user.display_name or user.username,
            "avatar_url": url_for('static', filename=user.avatar_url, _external=True) if user.avatar_url else None,
            "followers_count": user.followers.count(),
            
            "following_count": total_following, 
            
            "is_following": is_following
        }), 200
    except Exception as e:
        print(f"Lỗi API get_user_profile: {e}")
        return jsonify({"error": "Lỗi server"}), 500

@app.route('/api/mobile/follow', methods=['POST'])
def api_mobile_follow():
    try:
        data = request.json
        follower_username = data.get('follower_username') 
        followed_username = data.get('followed_username') 

        follower = User.query.filter_by(username=follower_username).first()
        followed = User.query.filter_by(username=followed_username).first()

        if not follower or not followed:
            return jsonify({"success": False, "message": "User không tồn tại"}), 404

        if follower.id == followed.id:
            return jsonify({"success": False, "message": "Không thể tự follow chính mình"}), 400

        is_following_now = False
        if follower.is_following(followed):
            follower.unfollow(followed) 
            message = "Đã bỏ theo dõi"
        else:
            follower.follow(followed) 
            is_following_now = True
            message = "Đã theo dõi"
            
            new_noti = Notification(
                user_id=followed.id, 
                sender_id=follower.id, 
                type='follow',
                message=f"{follower.display_name or follower.username} đã bắt đầu theo dõi bạn."
            )
            db.session.add(new_noti)

        db.session.commit()
        return jsonify({
            "success": True,
            "message": message,
            "is_following": is_following_now,
            "followers_count": followed.followers.count() 
        })
    except Exception as e:
        db.session.rollback()
        print(f"Lỗi API mobile follow: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    
from models import CommunityPost

@app.route('/api/community/post', methods=['POST'])
def api_create_community_post():
    try:
        data = request.json
        username = data.get('username')
        song_id = data.get('song_id')
        caption = data.get('caption', '')

        user = User.query.filter_by(username=username).first()
        if not user or not song_id:
            return jsonify({"success": False, "message": "Dữ liệu không hợp lệ"}), 400

        new_post = CommunityPost(user_id=user.id, song_id=song_id, caption=caption)
        db.session.add(new_post)
        db.session.commit()

        return jsonify({"success": True, "message": "Đã chia sẻ bài hát lên Cộng đồng!"}), 200
    except Exception as e:
        db.session.rollback()
        print(f"Lỗi tạo bài đăng: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    
@app.route('/api/story/upload', methods=['POST'])
def api_upload_story():
    try:
        username = request.form.get('username')
        if not username:
            return jsonify({"success": False, "message": "Thiếu username"}), 400
            
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"success": False, "message": "User không tồn tại"}), 404

        if 'image' not in request.files:
            return jsonify({"success": False, "message": "Không tìm thấy file ảnh"}), 400

        file = request.files['image']
        if file and allowed_file(file.filename, app.config.get('ALLOWED_IMAGE_EXTENSIONS', {'png', 'jpg', 'jpeg'})):
            filename_ext = file.filename.rsplit(".", 1)[1].lower()
            import time
            
            filename = secure_filename(f"story_{user.id}_{int(time.time())}.{filename_ext}")
            
            story_folder = os.path.join(app.config['STATIC_FOLDER'], 'img', 'stories')
            os.makedirs(story_folder, exist_ok=True)
            
            save_path = os.path.join(story_folder, filename)
            file.save(save_path)
            
            media_url = f"img/stories/{filename}"
            new_story = Story(user_id=user.id, media_url=media_url)
            
            db.session.add(new_story)
            db.session.commit()
            
            return jsonify({"success": True, "message": "Đã đăng Story thành công!"}), 200
        else:
            return jsonify({"success": False, "message": "File không hợp lệ"}), 400

    except Exception as e:
        db.session.rollback()
        print(f"Lỗi API upload story: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    
@app.route('/api/blend', methods=['GET'])
def get_blend_mix():
    user1_username = request.args.get('user1') 
    user2_username = request.args.get('user2')

    if not user1_username or not user2_username:
        return jsonify({"error": "Thiếu thông tin user"}), 400

    user1 = User.query.filter_by(username=user1_username).first()
    user2 = User.query.filter_by(username=user2_username).first()

    if not user1 or not user2:
        return jsonify({"error": "Không tìm thấy user"}), 404

    try:
        u1_liked_ids = [row.song_id for row in db.session.query(likes.c.song_id).filter(likes.c.user_id == user1.id).all()]
        u2_liked_ids = [row.song_id for row in db.session.query(likes.c.song_id).filter(likes.c.user_id == user2.id).all()]

        u1_history_ids = [row.song_id for row in db.session.query(ListeningHistory.song_id).filter(ListeningHistory.user_id == user1.id).order_by(desc(ListeningHistory.timestamp)).limit(50).all()]
        u2_history_ids = [row.song_id for row in db.session.query(ListeningHistory.song_id).filter(ListeningHistory.user_id == user2.id).order_by(desc(ListeningHistory.timestamp)).limit(50).all()]

        u1_all_ids = set(u1_liked_ids + u1_history_ids)
        u2_all_ids = set(u2_liked_ids + u2_history_ids)

        common_ids = list(u1_all_ids.intersection(u2_all_ids))
        u1_unique_ids = list(u1_all_ids - u2_all_ids)
        u2_unique_ids = list(u2_all_ids - u1_all_ids)

        import random
        random.shuffle(common_ids)
        random.shuffle(u1_unique_ids)
        random.shuffle(u2_unique_ids)

        final_ids = common_ids.copy()
        
        max_len = max(len(u1_unique_ids), len(u2_unique_ids))
        for i in range(max_len):
            if i < len(u1_unique_ids):
                final_ids.append(u1_unique_ids[i])
            if i < len(u2_unique_ids):
                final_ids.append(u2_unique_ids[i])

        final_ids = final_ids[:30]

        if not final_ids:
            fallback_songs = Song.query.order_by(func.random()).limit(15).all()
            final_ids = [s.id for s in fallback_songs]

        songs = Song.query.filter(Song.id.in_(final_ids)).all()
        song_map = {song.id: song for song in songs}
        blend_mix = [song_map[s_id] for s_id in final_ids if s_id in song_map]

        songs_data = []
        for song in blend_mix:
            songs_data.append({
                "id": song.id,
                "title": song.title,
                "artist": song.artist,
                "duration": song.duration,
                "cover_art_path": url_for('static', filename=song.cover_art_path, _external=True) if song.cover_art_path else None,
                "file_path": url_for('static', filename=song.file_path, _external=True) if song.file_path else None
            })

        return jsonify(songs_data), 200
    except Exception as e:
        print(f"Lỗi tạo Blend Mix: {e}")
        return jsonify({"error": "Lỗi server"}), 500
    
@app.route('/api/blend/create', methods=['POST'])
def create_blend_mix_api():
    try:
        data = request.json if request.is_json else request.form
        
        username = data.get('username')
        partner_username = data.get('partner_username')

        if not username or not partner_username:
            return jsonify({"success": False, "message": "Thiếu thông tin người dùng"}), 400

        user1 = User.query.filter_by(username=username).first()
        user2 = User.query.filter_by(username=partner_username).first()

        if not user1 or not user2:
            return jsonify({"success": False, "message": "Không tìm thấy người dùng"}), 404

        user1_name = user1.display_name or user1.username
        user2_name = user2.display_name or user2.username
        blend_name = f"Blend: {user1_name} & {user2_name}"
        blend_desc = f"Bản Mix kết hợp âm nhạc của {user1_name} và {user2_name}"

        new_blend = Playlist(
            name=blend_name,
            description=blend_desc,
            user_id=user1.id,
            is_public=False, 
            is_blend=True,
            partner_id=user2.id,
            invitation_accepted=False 
        )

        db.session.add(new_blend)
        db.session.flush() 

        u1_liked_ids = [row.song_id for row in db.session.query(likes.c.song_id).filter(likes.c.user_id == user1.id).all()]
        u2_liked_ids = [row.song_id for row in db.session.query(likes.c.song_id).filter(likes.c.user_id == user2.id).all()]

        u1_history_ids = [row.song_id for row in db.session.query(ListeningHistory.song_id).filter(ListeningHistory.user_id == user1.id).order_by(desc(ListeningHistory.timestamp)).limit(50).all()]
        u2_history_ids = [row.song_id for row in db.session.query(ListeningHistory.song_id).filter(ListeningHistory.user_id == user2.id).order_by(desc(ListeningHistory.timestamp)).limit(50).all()]

        u1_all_ids = set(u1_liked_ids + u1_history_ids)
        u2_all_ids = set(u2_liked_ids + u2_history_ids)

        common_ids = list(u1_all_ids.intersection(u2_all_ids))
        u1_unique_ids = list(u1_all_ids - u2_all_ids)
        u2_unique_ids = list(u2_all_ids - u1_all_ids)

        import random
        random.shuffle(common_ids)
        random.shuffle(u1_unique_ids)
        random.shuffle(u2_unique_ids)

        final_ids = common_ids.copy()
        
        max_len = max(len(u1_unique_ids), len(u2_unique_ids))
        for i in range(max_len):
            if i < len(u1_unique_ids):
                final_ids.append(u1_unique_ids[i])
            if i < len(u2_unique_ids):
                final_ids.append(u2_unique_ids[i])

        final_ids = final_ids[:30] 

        if not final_ids:
            fallback_songs = Song.query.order_by(func.random()).limit(15).all()
            final_ids = [s.id for s in fallback_songs]

        songs_to_add = Song.query.filter(Song.id.in_(final_ids)).all()
        for song in songs_to_add:
            new_blend.songs.append(song)

        new_noti = Notification(
            user_id=user2.id, 
            sender_id=user1.id, 
            type='blend_invite',
            message=f"{user1_name} đã tạo một bản Blend Mix cùng bạn!"
        )
        db.session.add(new_noti)

        db.session.commit()

        return jsonify({
            "success": True,
            "message": "Tạo Blend Mix thành công!",
            "playlist_id": new_blend.id
        }), 200

    except Exception as e:
        db.session.rollback()
        print(f"Lỗi API tạo Blend Mix: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"success": False, "message": "Lỗi server khi tạo Blend Mix"}), 500
    
@app.route('/api/mobile/user/update', methods=['POST'])
def mobile_update_profile():
    try:
        username = request.form.get('username')
        display_name = request.form.get('display_name')
        bio = request.form.get('bio')

        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"success": False, "message": "User không tồn tại"}), 404

        if display_name:
            user.display_name = display_name
        if bio is not None:
            user.bio = bio

        new_avatar_url = None
        if 'avatar' in request.files:
            file = request.files['avatar']
            if file and file.filename != '':
                filename_ext = file.filename.rsplit(".", 1)[1].lower()
                import time
                filename = secure_filename(f"avatar_{user.id}_{int(time.time())}.{filename_ext}")
                save_path = os.path.join(app.config['STATIC_FOLDER'], 'img', 'avatars', filename)
                
                file.save(save_path)
                relative_path = f"img/avatars/{filename}"
                user.avatar_url = relative_path
                new_avatar_url = url_for('static', filename=relative_path, _external=True)

        db.session.commit()
        
        return jsonify({
            "success": True, 
            "message": "Cập nhật hồ sơ thành công!",
            "avatar_url": new_avatar_url
        }), 200

    except Exception as e:
        db.session.rollback()
        print(f"Lỗi update profile mobile: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    
@app.route('/api/user/<username>/followers', methods=['GET'])
def get_user_followers(username):
    try:
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"success": False, "message": "User không tồn tại"}), 404
            
        followers_list = user.followers.all()
        data = []
        for f in followers_list:
            data.append({
                "id": f.id,
                "username": f.username,
                "display_name": f.display_name or f.username,
                "avatar_url": url_for('static', filename=f.avatar_url, _external=True) if f.avatar_url else url_for('static', filename='img/avatars/default_avatar.png', _external=True)
            })
            
        return jsonify(data), 200
    except Exception as e:
        print(f"Lỗi API get_user_followers: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500

@app.route('/api/user/<username>/following', methods=['GET'])
def get_user_following(username):
    try:
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"success": False, "message": "User không tồn tại"}), 404
            
        data = []

        following_users = user.followed.all()
        for f in following_users:
            data.append({
                "id": f.id,
                "username": f.username,
                "display_name": f.display_name or f.username,
                "avatar_url": url_for('static', filename=f.avatar_url, _external=True) if f.avatar_url else url_for('static', filename='img/avatars/default_avatar.png', _external=True),
                "type": "user" 
            })

        following_artists = user.followed_artists.all()
        for a in following_artists:
            data.append({
                "id": a.id,
                "username": a.name, 
                "display_name": a.name,
                "avatar_url": url_for('static', filename=a.profile_image_path, _external=True) if a.profile_image_path else url_for('static', filename='img/avatars/default_avatar.png', _external=True),
                "type": "artist" 
            })
            
        data.sort(key=lambda x: x['display_name'].lower())

        return jsonify(data), 200
    except Exception as e:
        print(f"Lỗi API get_user_following: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    
@app.route('/api/mobile/artist/follow', methods=['POST'])
def api_mobile_artist_follow():
    try:
        data = request.json
        username = data.get('username') 
        artist_name = data.get('artist_name') 

        user = User.query.filter_by(username=username).first()
        artist = Artist.query.filter_by(name=artist_name).first()

        if not user or not artist:
            return jsonify({"success": False, "message": "Dữ liệu không hợp lệ"}), 404

        is_following_now = False
        if user in artist.followers:
            artist.followers.remove(user)
            message = "Đã bỏ theo dõi nghệ sĩ"
        else:
            artist.followers.append(user)
            is_following_now = True
            message = "Đã theo dõi nghệ sĩ"

        db.session.commit()
        return jsonify({
            "success": True,
            "message": message,
            "is_following": is_following_now,
            "followers_count": artist.followers.count()
        }), 200
    except Exception as e:
        db.session.rollback()
        print(f"Lỗi API follow artist: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    
@app.route('/api/artist/<artist_name>/followers', methods=['GET'])
def get_artist_followers(artist_name):
    try:
        from urllib.parse import unquote
        artist_name_decoded = unquote(artist_name, encoding="utf-8")
        
        artist = Artist.query.filter_by(name=artist_name_decoded).first()
        if not artist:
            return jsonify([]), 200
            
        followers_list = artist.followers.all()
        data = []
        for f in followers_list:
            data.append({
                "id": f.id,
                "username": f.username,
                "display_name": f.display_name or f.username,
                "avatar_url": url_for('static', filename=f.avatar_url, _external=True) if f.avatar_url else url_for('static', filename='img/avatars/default_avatar.png', _external=True)
            })
            
        return jsonify(data), 200
    except Exception as e:
        print(f"Lỗi API get_artist_followers: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
    
from models import Notification 

@app.route('/api/notifications/unread/<username>', methods=['GET'])
def get_unread_notifications(username):
    try:
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"error": "User không tồn tại"}), 404

        unread_notifs = Notification.query.filter_by(user_id=user.id, is_read=False)\
                                        .order_by(Notification.created_at.desc()).all()

        data = []
        for n in unread_notifs:
            data.append({
                "id": n.id,
                "type": n.type,
                "message": n.message,
                "created_at": n.created_at.isoformat()
            })
            
            n.is_read = True

        db.session.commit()
        return jsonify(data), 200

    except Exception as e:
        db.session.rollback()
        print(f"Lỗi API get_unread_notifications: {e}")
        return jsonify({"error": "Lỗi server"}), 500
    
@app.route('/api/notifications/history/<username>', methods=['GET'])
def get_notification_history(username):
    try:
        user = User.query.filter_by(username=username).first()
        if not user:
            return jsonify({"error": "User không tồn tại"}), 404

        notifs = Notification.query.filter_by(user_id=user.id)\
                                   .order_by(Notification.created_at.desc()).limit(30).all()

        data = []
        for n in notifs:
            data.append({
                "id": n.id,
                "type": n.type,
                "message": n.message,
                "created_at": n.created_at.isoformat()
            })

        return jsonify(data), 200

    except Exception as e:
        print(f"Lỗi API get_notification_history: {e}")
        return jsonify({"error": "Lỗi server"}), 500
    
@app.route('/api/users/all', methods=['GET'])
def api_get_all_users():
    try:
        users = User.query.all()
        result = []
        for u in users:
            if u.role == 'admin': 
                continue 
                
            result.append({
                "username": u.username,
                "display_name": u.display_name or u.username,
                "avatar_url": url_for('static', filename=u.avatar_url, _external=True) if u.avatar_url else url_for('static', filename='img/avatars/default_avatar.png', _external=True)
            })
            
        return jsonify(result), 200
        
    except Exception as e:
        print(f"Lỗi API get all users: {e}")
        return jsonify({"success": False, "message": "Lỗi server"}), 500
            
if __name__ == "__main__":

    if not os.path.exists(instance_folder_path):
        os.makedirs(instance_folder_path)
        print(f"Creating instance folder at: {instance_folder_path}")

    print("Starting Flask app...")

    app.run(debug=True, host="0.0.0.0", port=5001, threaded=True)
