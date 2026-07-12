package com.villxin.bandapi.community.dto;

import com.villxin.bandapi.community.entity.Board;
import com.villxin.bandapi.community.entity.Bulletin;
import com.villxin.bandapi.community.entity.DmMessage;
import com.villxin.bandapi.community.entity.DmRequest;
import com.villxin.bandapi.community.entity.ForumReply;
import com.villxin.bandapi.community.entity.ForumThread;
import com.villxin.bandapi.community.entity.WallComment;
import com.villxin.bandapi.entity.User;

import java.time.Instant;
import java.util.List;

/**
 * Explicit response DTOs for the community API — entities are never
 * serialized directly (no email leakage, no lazy-loading surprises).
 */
public final class CommunityDtos {

    private CommunityDtos() {}

    private static final int PREVIEW_LENGTH = 120;

    public static String preview(String body) {
        if (body == null) return "";
        return body.length() <= PREVIEW_LENGTH ? body : body.substring(0, PREVIEW_LENGTH) + "…";
    }

    public record UserSummaryDto(String username, String displayName, String avatarUrl, boolean official) {
        public static UserSummaryDto from(User u) {
            return new UserSummaryDto(u.getUsername(), u.getDisplayName(), u.getAvatarUrl(), u.isOfficial());
        }
    }

    public record ProfileDto(String username, String displayName, String avatarUrl,
                             String about, String whoToMeet, String mood, String profileSong,
                             boolean official, String themeAccent, boolean themeGlitter,
                             boolean themeTiledBg, Instant memberSince,
                             List<UserSummaryDto> topFriends) {
        public static ProfileDto from(User u, List<UserSummaryDto> topFriends) {
            return new ProfileDto(u.getUsername(), u.getDisplayName(), u.getAvatarUrl(),
                    u.getAbout(), u.getWhoToMeet(), u.getMood(), u.getProfileSong(),
                    u.isOfficial(), u.getThemeAccent().name(), u.isThemeGlitter(),
                    u.isThemeTiledBg(), u.getCreatedAt(), topFriends);
        }
    }

    public record BoardDto(Long id, String slug, String title, String description, long threadCount) {
        public static BoardDto from(Board b, long threadCount) {
            return new BoardDto(b.getId(), b.getSlug(), b.getTitle(), b.getDescription(), threadCount);
        }
    }

    public record ThreadSummaryDto(Long id, String title, UserSummaryDto author,
                                   Instant createdAt, long replyCount) {
        public static ThreadSummaryDto from(ForumThread t, long replyCount) {
            return new ThreadSummaryDto(t.getId(), t.getTitle(), UserSummaryDto.from(t.getAuthor()),
                    t.getCreatedAt(), replyCount);
        }
    }

    public record ReplyDto(Long id, String body, UserSummaryDto author, Instant createdAt) {
        public static ReplyDto from(ForumReply r) {
            return new ReplyDto(r.getId(), r.getBody(), UserSummaryDto.from(r.getAuthor()), r.getCreatedAt());
        }
    }

    public record ThreadDetailDto(Long id, String boardSlug, String boardTitle, String title, String body,
                                  UserSummaryDto author, Instant createdAt, List<ReplyDto> replies) {
        public static ThreadDetailDto from(ForumThread t, List<ReplyDto> replies) {
            return new ThreadDetailDto(t.getId(), t.getBoard().getSlug(), t.getBoard().getTitle(),
                    t.getTitle(), t.getBody(), UserSummaryDto.from(t.getAuthor()), t.getCreatedAt(), replies);
        }
    }

    public record BulletinDto(Long id, String title, String body, UserSummaryDto author, Instant createdAt) {
        public static BulletinDto from(Bulletin b) {
            return new BulletinDto(b.getId(), b.getTitle(), b.getBody(),
                    UserSummaryDto.from(b.getAuthor()), b.getCreatedAt());
        }
    }

    public record WallCommentDto(Long id, String body, boolean glitter,
                                 UserSummaryDto author, Instant createdAt) {
        public static WallCommentDto from(WallComment c) {
            return new WallCommentDto(c.getId(), c.getBody(), c.isGlitter(),
                    UserSummaryDto.from(c.getAuthor()), c.getCreatedAt());
        }
    }

    // --- DMs ---

    public record DmRequestDto(Long id, UserSummaryDto from, String preview, Instant createdAt) {
        public static DmRequestDto from(DmRequest r) {
            return new DmRequestDto(r.getId(), UserSummaryDto.from(r.getSender()),
                    CommunityDtos.preview(r.getBody()), r.getCreatedAt());
        }
    }

    public record InboxThreadDto(Long id, UserSummaryDto with, String lastMessagePreview,
                                 Instant lastActivityAt, long unreadCount, String status) {}

    public record DmMessageDto(Long id, String from, String body, Instant createdAt) {
        public static DmMessageDto from(DmMessage m) {
            return new DmMessageDto(m.getId(), m.getSender().getUsername(), m.getBody(), m.getCreatedAt());
        }
    }

    public record ThreadMessagesDto(Long id, UserSummaryDto with, String status, List<DmMessageDto> messages) {}

    /** Sent folder: outgoing requests (pending AND declined look identical) + sent messages. */
    public record SentRequestDto(Long id, UserSummaryDto to, String preview, Instant sentAt) {}

    public record SentMessageDto(Long threadId, UserSummaryDto to, String body, Instant sentAt) {}

    public record SentFolderDto(List<SentRequestDto> requests, List<SentMessageDto> messages) {}
}
