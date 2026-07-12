package com.villxin.bandapi.community.controller;

import com.villxin.bandapi.community.dto.CommunityDtos.BoardDto;
import com.villxin.bandapi.community.dto.CommunityDtos.ReplyDto;
import com.villxin.bandapi.community.dto.CommunityDtos.ThreadDetailDto;
import com.villxin.bandapi.community.dto.CommunityDtos.ThreadSummaryDto;
import com.villxin.bandapi.community.entity.Board;
import com.villxin.bandapi.community.entity.ForumReply;
import com.villxin.bandapi.community.entity.ForumThread;
import com.villxin.bandapi.community.repository.BoardRepository;
import com.villxin.bandapi.community.repository.ForumReplyRepository;
import com.villxin.bandapi.community.repository.ForumThreadRepository;
import com.villxin.bandapi.community.service.CommunityUserService;
import com.villxin.bandapi.community.service.WordFilterService;
import com.villxin.bandapi.entity.User;
import com.villxin.bandapi.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Forum: boards -> threads -> replies. Public read, member write. */
@RestController
@RequestMapping("/api/community")
public class ForumController {

    private final BoardRepository boardRepository;
    private final ForumThreadRepository threadRepository;
    private final ForumReplyRepository replyRepository;
    private final CommunityUserService users;
    private final WordFilterService wordFilter;

    public ForumController(BoardRepository boardRepository,
                           ForumThreadRepository threadRepository,
                           ForumReplyRepository replyRepository,
                           CommunityUserService users,
                           WordFilterService wordFilter) {
        this.boardRepository = boardRepository;
        this.threadRepository = threadRepository;
        this.replyRepository = replyRepository;
        this.users = users;
        this.wordFilter = wordFilter;
    }

    @GetMapping("/boards")
    public List<BoardDto> boards() {
        return boardRepository.findAllByOrderByPositionAsc().stream()
                .map(b -> BoardDto.from(b, threadRepository.countByBoardId(b.getId())))
                .toList();
    }

    @GetMapping("/boards/{slug}/threads")
    public Page<ThreadSummaryDto> threads(@PathVariable String slug,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Board board = requireBoard(slug);
        return threadRepository
                .findByBoardIdOrderByCreatedAtDesc(board.getId(), PageRequest.of(page, Math.min(size, 50)))
                .map(t -> ThreadSummaryDto.from(t, replyRepository.countByThreadId(t.getId())));
    }

    @PostMapping("/boards/{slug}/threads")
    public ResponseEntity<ThreadDetailDto> createThread(@PathVariable String slug,
                                                        @Valid @RequestBody ThreadRequest request,
                                                        Authentication auth) {
        User author = users.requireMember(auth);
        Board board = requireBoard(slug);

        ForumThread thread = new ForumThread();
        thread.setBoard(board);
        thread.setAuthor(author);
        thread.setTitle(wordFilter.filter(request.title()));
        thread.setBody(wordFilter.filter(request.body()));
        thread = threadRepository.save(thread);
        return ResponseEntity.ok(ThreadDetailDto.from(thread, List.of()));
    }

    @GetMapping("/threads/{id}")
    public ThreadDetailDto thread(@PathVariable Long id) {
        ForumThread thread = threadRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("No thread " + id));
        List<ReplyDto> replies = replyRepository.findByThreadIdOrderByCreatedAtAsc(id)
                .stream().map(ReplyDto::from).toList();
        return ThreadDetailDto.from(thread, replies);
    }

    @PostMapping("/threads/{id}/replies")
    public ResponseEntity<ReplyDto> reply(@PathVariable Long id,
                                          @Valid @RequestBody ReplyRequest request,
                                          Authentication auth) {
        User author = users.requireMember(auth);
        ForumThread thread = threadRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("No thread " + id));

        ForumReply reply = new ForumReply();
        reply.setThread(thread);
        reply.setAuthor(author);
        reply.setBody(wordFilter.filter(request.body()));
        return ResponseEntity.ok(ReplyDto.from(replyRepository.save(reply)));
    }

    private Board requireBoard(String slug) {
        return boardRepository.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("No board " + slug));
    }

    record ThreadRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10000) String body
    ) {}

    record ReplyRequest(@NotBlank @Size(max = 10000) String body) {}
}
