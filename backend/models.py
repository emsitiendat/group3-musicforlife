from flask_sqlalchemy import SQLAlchemy
from flask_login import UserMixin
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime
from sqlalchemy import event
from unidecode import unidecode
from sqlalchemy import MetaData

convention = {
    "ix": 'ix_%(column_0_label)s',
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s"
}

metadata = MetaData(naming_convention=convention)

db = SQLAlchemy(metadata=metadata)


likes = db.Table(
    "likes",
    db.Column(
        "user_id",
        db.Integer,
        db.ForeignKey("user.id", ondelete="CASCADE"),
        primary_key=True,
    ),
    db.Column(
        "song_id",
        db.Integer,
        db.ForeignKey("song.id", ondelete="CASCADE"),
        primary_key=True,
    ),
)


playlist_songs = db.Table(
    "playlist_songs",
    db.Column(
        "playlist_id",
        db.Integer,
        db.ForeignKey("playlist.id", ondelete="CASCADE"),
        primary_key=True,
    ),
    db.Column(
        "song_id",
        db.Integer,
        db.ForeignKey("song.id", ondelete="CASCADE"),
        primary_key=True,
    ),
)

followers = db.Table(
    "followers",
    db.Column(
        "follower_id",
        db.Integer,
        db.ForeignKey("user.id", ondelete="CASCADE"),
        primary_key=True,
    ),
    db.Column(
        "followed_id",
        db.Integer,
        db.ForeignKey("user.id", ondelete="CASCADE"),
        primary_key=True,
    ),
    db.Column("timestamp", db.DateTime, default=datetime.utcnow),
)

artist_followers = db.Table('artist_followers',
    db.Column('user_id', db.Integer, db.ForeignKey('user.id', ondelete='CASCADE'), primary_key=True),
    db.Column('artist_id', db.Integer, db.ForeignKey('artist.id', ondelete='CASCADE'), primary_key=True),
    db.Column('timestamp', db.DateTime, default=datetime.utcnow)
)


class User(UserMixin, db.Model):
    __tablename__ = "user"
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(80), unique=True, nullable=False, index=True)
    password_hash = db.Column(db.String(255))
    role = db.Column(db.String(20), nullable=False, default="user")
    username_normalized = db.Column(db.String(80), index=True)
    display_name_normalized = db.Column(db.String(100), index=True)

    display_name = db.Column(db.String(100), nullable=True)
    bio = db.Column(db.Text, nullable=True)
    avatar_url = db.Column(db.String(255), nullable=True)
    profile_visibility = db.Column(
        db.String(10), nullable=False, default="public", index=True
    )
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)

    playlists = db.relationship(
        "Playlist", 
        backref="owner", 
        lazy="dynamic", 
        cascade="all, delete-orphan",
        foreign_keys="Playlist.user_id" 
    )
    listens = db.relationship(
        "ListeningHistory",
        backref="listener",
        lazy="dynamic",
        cascade="all, delete-orphan",
    )

    liked_songs = db.relationship(
        "Song",
        secondary=likes,
        lazy="dynamic",
        backref=db.backref("liked_by_users", lazy="dynamic"),
    )
    comments = db.relationship(
        "Comment", backref="author", lazy="dynamic", cascade="all, delete-orphan"
    )

    followed = db.relationship(
        "User",
        secondary=followers,
        primaryjoin=(followers.c.follower_id == id),
        secondaryjoin=(followers.c.followed_id == id),
        backref=db.backref("followers", lazy="dynamic"),
        lazy="dynamic",
    )

    def is_admin(self):
        return self.role == "admin"

    def set_password(self, password):
        self.password_hash = generate_password_hash(password)

    def check_password(self, password):
        return check_password_hash(self.password_hash or "", password)

    def has_liked_song(self, song):
        return self.liked_songs.filter(likes.c.song_id == song.id).count() > 0

    def like_song(self, song):
        if not self.has_liked_song(song):
            self.liked_songs.append(song)

    def unlike_song(self, song):
        if self.has_liked_song(song):
            self.liked_songs.remove(song)

    def follow(self, user):
        """Theo dõi một user khác."""

        if not self.is_following(user) and self.id != user.id:
            self.followed.append(user)
            return self

    def unfollow(self, user):
        """Bỏ theo dõi một user."""
        if self.is_following(user):
            self.followed.remove(user)
            return self

    def is_following(self, user):
        """Kiểm tra xem có đang theo dõi user này không."""

        return self.followed.filter(followers.c.followed_id == user.id).count() > 0

    @property
    def following_count(self):
        """Trả về số lượng người dùng đang được theo dõi bởi user này."""
        return self.followed.count()

    @property
    def followers_count(self):
        """Trả về số lượng người dùng đang theo dõi user này."""

        return self.followers.count()

    @property
    def name_display(self):
        return self.display_name or self.username

    def __repr__(self):
        return f"<User {self.username}>"

class Story(db.Model):
    __tablename__ = "story"
    id = db.Column(db.Integer, primary_key=True)
    
    user_id = db.Column(
        db.Integer,
        db.ForeignKey("user.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    
    media_url = db.Column(db.String(500), nullable=False)
    
    created_at = db.Column(
        db.DateTime, nullable=False, default=datetime.utcnow, index=True
    )

    user = db.relationship("User", backref=db.backref("stories", lazy="dynamic", cascade="all, delete-orphan"))

    def __repr__(self):
        return f"<Story id={self.id} user_id={self.user_id}>"   

def normalize_text(text):
    """Chuyển thành chữ thường và loại bỏ dấu."""
    if not text:
        return ""

    try:
        return unidecode(text).lower()
    except Exception:

        return text.lower()


class Artist(db.Model):
    __tablename__ = "artist"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(150), unique=True, nullable=False, index=True)
    bio = db.Column(db.Text, nullable=True)
    profile_image_path = db.Column(db.String(255), nullable=True)
    banner_image_path = db.Column(db.String(255), nullable=True)

    name_normalized = db.Column(db.String(150), index=True, nullable=True)
    followers = db.relationship('User', secondary=artist_followers, lazy='dynamic', backref=db.backref('followed_artists', lazy='dynamic'))

    def __repr__(self):
        return f"<Artist {self.name}>"


@event.listens_for(Artist, "before_insert")
@event.listens_for(Artist, "before_update")
def before_artist_save(mapper, connection, target):
    """Tự động cập nhật trường name_normalized cho Artist."""
    if target.name:
        target.name_normalized = normalize_text(target.name)


class Song(db.Model):
    __tablename__ = "song"
    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(100), nullable=False, index=True)
    artist = db.Column(db.String(100), nullable=False, index=True)
    file_path = db.Column(db.String(255), nullable=False, unique=True)
    cover_art_path = db.Column(db.String(255), nullable=True)
    lyrics = db.Column(db.Text, nullable=True)

    listens = db.relationship(
        "ListeningHistory",
        backref="song_played",
        lazy="dynamic",
        cascade="all, delete-orphan",
    )
    comments = db.relationship(
        "Comment", backref="song", lazy="dynamic", cascade="all, delete-orphan"
    )
    share_count = db.Column(db.Integer, default=0, nullable=False)
    duration = db.Column(db.Integer, nullable=True)
    uploaded_at = db.Column(db.DateTime, default=datetime.utcnow)
    title_normalized = db.Column(db.String(100), index=True)
    artist_normalized = db.Column(db.String(100), index=True)

    @property
    def like_count(self):
        return self.liked_by_users.count()

    def __repr__(self):
        return f"<Song {self.title}>"


@event.listens_for(User, "before_insert")
@event.listens_for(User, "before_update")
def before_user_save(mapper, connection, target):
    """Tự động cập nhật các trường chuẩn hóa cho User."""
    if hasattr(target, "username"):
        target.username_normalized = normalize_text(target.username)

    if hasattr(target, "display_name"):
        target.display_name_normalized = (
            normalize_text(target.display_name) if target.display_name else None
        )


@event.listens_for(Song, "before_insert")
@event.listens_for(Song, "before_update")
def before_song_save(mapper, connection, target):
    """Tự động cập nhật các trường chuẩn hóa."""
    if hasattr(target, "title"):
        target.title_normalized = normalize_text(target.title)
    if hasattr(target, "artist"):
        target.artist_normalized = normalize_text(target.artist)


class Playlist(db.Model):
    __tablename__ = "playlist"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(100), nullable=False)
    description = db.Column(db.Text, nullable=True) 
    user_id = db.Column(
        db.Integer,
        db.ForeignKey("user.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    songs = db.relationship(
        "Song",
        secondary=playlist_songs,
        lazy="dynamic",
        backref=db.backref("playlists", lazy="dynamic"),
    )
    is_public = db.Column(db.Boolean, nullable=False, default=True, index=True)
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)
    custom_cover_path = db.Column(db.String(255), nullable=True)
    is_pinned = db.Column(db.Boolean, nullable=False, default=False, index=True)
    is_featured = db.Column(db.Boolean, nullable=False, default=False, index=True)
    is_blend = db.Column(db.Boolean, default=False) 
    partner_id = db.Column(db.Integer, db.ForeignKey("user.id"), nullable=True) 
    invitation_accepted = db.Column(db.Boolean, default=False)
    partner = db.relationship("User", foreign_keys=[partner_id])

    def __repr__(self):
        return f"<Playlist {self.name}>"


class ListeningHistory(db.Model):
    __tablename__ = "listening_history"
    id = db.Column(db.Integer, primary_key=True)

    user_id = db.Column(
        db.Integer,
        db.ForeignKey("user.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    song_id = db.Column(
        db.Integer,
        db.ForeignKey("song.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    timestamp = db.Column(
        db.DateTime, nullable=False, default=datetime.utcnow, index=True
    )

    def __repr__(self):
        return f"<Listen user={self.user_id} song={self.song_id} at {self.timestamp}>"


class Comment(db.Model):
    __tablename__ = "comment"
    id = db.Column(db.Integer, primary_key=True)
    text = db.Column(db.Text, nullable=False)
    timestamp = db.Column(db.DateTime, index=True, default=datetime.utcnow)

    user_id = db.Column(
        db.Integer,
        db.ForeignKey("user.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    song_id = db.Column(
        db.Integer,
        db.ForeignKey("song.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
class CommunityPost(db.Model):
    __tablename__ = "community_post"
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey("user.id", ondelete="CASCADE"), nullable=False, index=True)
    song_id = db.Column(db.Integer, db.ForeignKey("song.id", ondelete="CASCADE"), nullable=False, index=True)
    caption = db.Column(db.Text, nullable=True)
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.utcnow, index=True)

    user = db.relationship("User", backref=db.backref("community_posts", lazy="dynamic", cascade="all, delete-orphan"))
    song = db.relationship("Song")
    
class Notification(db.Model):
    __tablename__ = 'notification'
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id', ondelete='CASCADE'), nullable=False, index=True) 
    sender_id = db.Column(db.Integer, db.ForeignKey('user.id', ondelete='CASCADE'), nullable=True)
    type = db.Column(db.String(50), nullable=False) 
    message = db.Column(db.String(255), nullable=False) 
    is_read = db.Column(db.Boolean, default=False)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)

    sender = db.relationship("User", foreign_keys=[sender_id])

    def __repr__(self):
        return f"<Notification user_id={self.user_id} type={self.type}>"

    def __repr__(self):
        return f"<CommunityPost id={self.id} user={self.user_id} song={self.song_id}>"

    def __repr__(self):
        return f"<Comment {self.text[:20]}...>"
