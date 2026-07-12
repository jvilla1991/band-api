package com.villxin.bandapi.community.service;

import com.villxin.bandapi.community.dto.CommunityDtos.DmMessageDto;
import com.villxin.bandapi.community.dto.CommunityDtos.DmRequestDto;
import com.villxin.bandapi.community.dto.CommunityDtos.InboxThreadDto;
import com.villxin.bandapi.community.dto.CommunityDtos.SentFolderDto;
import com.villxin.bandapi.community.dto.CommunityDtos.SentMessageDto;
import com.villxin.bandapi.community.dto.CommunityDtos.SentRequestDto;
import com.villxin.bandapi.community.dto.CommunityDtos.ThreadMessagesDto;
import com.villxin.bandapi.community.dto.CommunityDtos.UserSummaryDto;
import com.villxin.bandapi.community.entity.DmMessage;
import com.villxin.bandapi.community.entity.DmRequest;
import com.villxin.bandapi.community.entity.DmThread;
import com.villxin.bandapi.community.entity.DmThreadState;
import com.villxin.bandapi.community.repository.DmMessageRepository;
import com.villxin.bandapi.community.repository.DmRequestRepository;
import com.villxin.bandapi.community.repository.DmThreadRepository;
import com.villxin.bandapi.community.repository.DmThreadStateRepository;
import com.villxin.bandapi.entity.User;
import com.villxin.bandapi.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.villxin.bandapi.community.dto.CommunityDtos.preview;

/**
 * Request-gated DMs. First contact creates a PENDING request; the recipient
 * accepts (thread opens, request body becomes the first message) or declines
 * (sender is never told — it still looks "sent"). Accepts are revocable:
 * revoking closes the thread both ends (block); only the revoker can reopen.
 */
@Service
public class DmService {

    private final DmRequestRepository requestRepository;
    private final DmThreadRepository threadRepository;
    private final DmMessageRepository messageRepository;
    private final DmThreadStateRepository stateRepository;
    private final CommunityUserService users;
    private final WordFilterService wordFilter;

    public DmService(DmRequestRepository requestRepository,
                     DmThreadRepository threadRepository,
                     DmMessageRepository messageRepository,
                     DmThreadStateRepository stateRepository,
                     CommunityUserService users,
                     WordFilterService wordFilter) {
        this.requestRepository = requestRepository;
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.stateRepository = stateRepository;
        this.users = users;
        this.wordFilter = wordFilter;
    }

    // --- requests ---

    /**
     * Sends a message request. If the sender is blocked (recipient revoked the
     * thread), the request is silently swallowed — the response still looks
     * like a success so the sender can't tell they're blocked.
     */
    @Transactional
    public void sendRequest(User sender, String recipientUsername, String body) {
        User recipient = users.requireByUsername(recipientUsername);
        if (recipient.getId().equals(sender.getId())) {
            throw ApiException.badRequest("INVALID_RECIPIENT", "You can't message yourself");
        }

        Optional<DmThread> existing = findPairThread(sender, recipient);
        if (existing.isPresent()) {
            DmThread thread = existing.get();
            if (thread.getStatus() == DmThread.Status.OPEN) {
                throw ApiException.conflict("THREAD_EXISTS", "You already have an open thread — message there");
            }
            // REVOKED:
            if (thread.getRevokedBy() != null && thread.getRevokedBy().getId().equals(sender.getId())) {
                throw ApiException.conflict("THREAD_REVOKED", "You revoked this thread — reopen it instead");
            }
            return; // sender is blocked: quietly drop, still "looks sent"
        }

        // idempotent: an outstanding pending request already "looks sent"
        if (requestRepository.findFirstBySenderIdAndRecipientIdAndStatus(
                sender.getId(), recipient.getId(), DmRequest.Status.PENDING).isPresent()) {
            return;
        }

        DmRequest request = new DmRequest();
        request.setSender(sender);
        request.setRecipient(recipient);
        request.setBody(wordFilter.filter(body));
        requestRepository.save(request);
    }

    public List<DmRequestDto> incomingRequests(User user) {
        return requestRepository
                .findByRecipientIdAndStatusOrderByCreatedAtDesc(user.getId(), DmRequest.Status.PENDING)
                .stream().map(DmRequestDto::from).toList();
    }

    /** Accept: opens (or reopens) the pair thread; the request body becomes its first message. */
    @Transactional
    public Long accept(User user, Long requestId) {
        DmRequest request = requireOwnPendingRequest(user, requestId);
        request.setStatus(DmRequest.Status.ACCEPTED);
        request.setResolvedAt(Instant.now());
        requestRepository.save(request);

        DmThread thread = findPairThread(request.getSender(), request.getRecipient())
                .orElseGet(() -> newPairThread(request.getSender(), request.getRecipient()));
        thread.setStatus(DmThread.Status.OPEN);
        thread.setRevokedBy(null);
        thread = threadRepository.save(thread);

        DmMessage first = new DmMessage();
        first.setThread(thread);
        first.setSender(request.getSender());
        first.setBody(request.getBody()); // already filtered at request time
        messageRepository.save(first);

        untrash(thread, request.getSender());
        untrash(thread, request.getRecipient());
        return thread.getId();
    }

    /** Decline: request quietly dies. The sender is never notified. */
    @Transactional
    public void decline(User user, Long requestId) {
        DmRequest request = requireOwnPendingRequest(user, requestId);
        request.setStatus(DmRequest.Status.DECLINED);
        request.setResolvedAt(Instant.now());
        requestRepository.save(request);
    }

    // --- threads & messages ---

    @Transactional
    public DmMessageDto postMessage(User user, Long threadId, String body) {
        DmThread thread = requireParticipantThread(user, threadId);
        if (thread.getStatus() != DmThread.Status.OPEN) {
            throw ApiException.forbidden("THREAD_CLOSED", "This thread is closed");
        }
        DmMessage message = new DmMessage();
        message.setThread(thread);
        message.setSender(user);
        message.setBody(wordFilter.filter(body));
        message = messageRepository.save(message);

        // a new message resurfaces the thread for the other side even if they trashed it
        untrash(thread, thread.otherParticipant(user.getId()));
        return DmMessageDto.from(message);
    }

    /** Reading a thread marks it read for the requesting user. */
    @Transactional
    public ThreadMessagesDto getThread(User user, Long threadId) {
        DmThread thread = requireParticipantThread(user, threadId);
        DmThreadState state = getOrCreateState(thread, user);
        state.setLastReadAt(Instant.now());
        stateRepository.save(state);

        List<DmMessageDto> messages = messageRepository.findByThreadIdOrderByCreatedAtAsc(threadId)
                .stream().map(DmMessageDto::from).toList();
        return new ThreadMessagesDto(thread.getId(),
                UserSummaryDto.from(thread.otherParticipant(user.getId())),
                thread.getStatus().name(), messages);
    }

    /** "Revoke access": closes the thread both ends (block). */
    @Transactional
    public void revoke(User user, Long threadId) {
        DmThread thread = requireParticipantThread(user, threadId);
        thread.setStatus(DmThread.Status.REVOKED);
        thread.setRevokedBy(user);
        threadRepository.save(thread);
    }

    /** Re-accept: only the revoker can reopen a revoked thread. */
    @Transactional
    public void reopen(User user, Long threadId) {
        DmThread thread = requireParticipantThread(user, threadId);
        if (thread.getStatus() != DmThread.Status.REVOKED
                || thread.getRevokedBy() == null
                || !thread.getRevokedBy().getId().equals(user.getId())) {
            throw ApiException.forbidden("NOT_REVOKER", "Only the member who revoked access can reopen");
        }
        thread.setStatus(DmThread.Status.OPEN);
        thread.setRevokedBy(null);
        threadRepository.save(thread);
    }

    @Transactional
    public void setTrashed(User user, Long threadId, boolean trashed) {
        DmThread thread = requireParticipantThread(user, threadId);
        DmThreadState state = getOrCreateState(thread, user);
        state.setTrashed(trashed);
        stateRepository.save(state);
    }

    // --- folders ---

    /** Inbox: open threads (plus revoked ones for the revoker, so they can reopen), not trashed. */
    public List<InboxThreadDto> inbox(User user) {
        return threadRepository.findAllForUser(user.getId()).stream()
                .filter(t -> visibleInInbox(t, user))
                .filter(t -> !isTrashed(t, user))
                .map(t -> toInboxDto(t, user))
                .sorted(Comparator.comparing(InboxThreadDto::lastActivityAt).reversed())
                .toList();
    }

    public List<InboxThreadDto> trashFolder(User user) {
        return threadRepository.findAllForUser(user.getId()).stream()
                .filter(t -> isTrashed(t, user))
                .map(t -> toInboxDto(t, user))
                .sorted(Comparator.comparing(InboxThreadDto::lastActivityAt).reversed())
                .toList();
    }

    /** Sent: my messages plus outgoing requests — declined ones look exactly like pending ones. */
    public SentFolderDto sentFolder(User user) {
        List<SentRequestDto> requests = requestRepository
                .findBySenderIdAndStatusInOrderByCreatedAtDesc(user.getId(),
                        List.of(DmRequest.Status.PENDING, DmRequest.Status.DECLINED))
                .stream()
                .map(r -> new SentRequestDto(r.getId(), UserSummaryDto.from(r.getRecipient()),
                        preview(r.getBody()), r.getCreatedAt()))
                .toList();
        List<SentMessageDto> messages = messageRepository
                .findBySenderIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(m -> new SentMessageDto(m.getThread().getId(),
                        UserSummaryDto.from(m.getThread().otherParticipant(user.getId())),
                        m.getBody(), m.getCreatedAt()))
                .toList();
        return new SentFolderDto(requests, messages);
    }

    // --- helpers ---

    private boolean visibleInInbox(DmThread thread, User user) {
        if (thread.getStatus() == DmThread.Status.OPEN) return true;
        return thread.getRevokedBy() != null && thread.getRevokedBy().getId().equals(user.getId());
    }

    private InboxThreadDto toInboxDto(DmThread thread, User user) {
        Optional<DmMessage> last = messageRepository.findFirstByThreadIdOrderByCreatedAtDesc(thread.getId());
        Instant lastActivity = last.map(DmMessage::getCreatedAt).orElse(thread.getCreatedAt());
        Instant lastRead = stateRepository.findByThreadIdAndUserId(thread.getId(), user.getId())
                .map(DmThreadState::getLastReadAt).orElse(null);
        long unread = (lastRead == null)
                ? messageRepository.countByThreadIdAndSenderIdNot(thread.getId(), user.getId())
                : messageRepository.countByThreadIdAndSenderIdNotAndCreatedAtAfter(thread.getId(), user.getId(), lastRead);
        return new InboxThreadDto(thread.getId(),
                UserSummaryDto.from(thread.otherParticipant(user.getId())),
                last.map(m -> preview(m.getBody())).orElse(""),
                lastActivity, unread, thread.getStatus().name());
    }

    private boolean isTrashed(DmThread thread, User user) {
        return stateRepository.findByThreadIdAndUserId(thread.getId(), user.getId())
                .map(DmThreadState::isTrashed).orElse(false);
    }

    private void untrash(DmThread thread, User user) {
        stateRepository.findByThreadIdAndUserId(thread.getId(), user.getId()).ifPresent(state -> {
            if (state.isTrashed()) {
                state.setTrashed(false);
                stateRepository.save(state);
            }
        });
    }

    private DmThreadState getOrCreateState(DmThread thread, User user) {
        return stateRepository.findByThreadIdAndUserId(thread.getId(), user.getId())
                .orElseGet(() -> {
                    DmThreadState state = new DmThreadState();
                    state.setThread(thread);
                    state.setUser(user);
                    return state;
                });
    }

    private DmRequest requireOwnPendingRequest(User user, Long requestId) {
        return requestRepository.findById(requestId)
                .filter(r -> r.getRecipient().getId().equals(user.getId()))
                .filter(r -> r.getStatus() == DmRequest.Status.PENDING)
                .orElseThrow(() -> ApiException.notFound("No pending request " + requestId));
    }

    private DmThread requireParticipantThread(User user, Long threadId) {
        return threadRepository.findById(threadId)
                .filter(t -> t.hasParticipant(user.getId()))
                .orElseThrow(() -> ApiException.notFound("No thread " + threadId));
    }

    private Optional<DmThread> findPairThread(User u1, User u2) {
        long lowId = Math.min(u1.getId(), u2.getId());
        long highId = Math.max(u1.getId(), u2.getId());
        return threadRepository.findByUserAIdAndUserBId(lowId, highId);
    }

    private DmThread newPairThread(User u1, User u2) {
        DmThread thread = new DmThread();
        if (u1.getId() < u2.getId()) {
            thread.setUserA(u1);
            thread.setUserB(u2);
        } else {
            thread.setUserA(u2);
            thread.setUserB(u1);
        }
        return thread;
    }
}
