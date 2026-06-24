package com.villxin.bandapi.controller;

import com.villxin.bandapi.entity.Post;
import com.villxin.bandapi.entity.Reply;
import com.villxin.bandapi.repository.PostRepository;
import com.villxin.bandapi.repository.ReplyRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;

    public PostController(PostRepository postRepository, ReplyRepository replyRepository) {
        this.postRepository = postRepository;
        this.replyRepository = replyRepository;
    }

    @GetMapping
    public Page<Post> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 50)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Post> get(@PathVariable Long id) {
        return postRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Post> create(@Valid @RequestBody PostRequest request,
                                       Authentication auth) {
        Post post = new Post();
        post.setTitle(request.title());
        post.setBody(request.body());

        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            post.setAuthorEmail(email);
            post.setAuthorName(email.substring(0, email.indexOf('@')));
        } else {
            post.setAuthorName(request.displayName() != null ? request.displayName() : "Anonymous");
        }

        return ResponseEntity.ok(postRepository.save(post));
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<?> reply(@PathVariable Long id,
                                   @Valid @RequestBody ReplyRequest request,
                                   Authentication auth) {
        return postRepository.findById(id).map(post -> {
            Reply reply = new Reply();
            reply.setPost(post);
            reply.setBody(request.body());

            if (auth != null && auth.isAuthenticated()) {
                String email = auth.getName();
                reply.setAuthorEmail(email);
                reply.setAuthorName(email.substring(0, email.indexOf('@')));
            } else {
                reply.setAuthorName(request.displayName() != null ? request.displayName() : "Anonymous");
            }

            return ResponseEntity.ok(replyRepository.save(reply));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deletePost(@PathVariable Long id) {
        if (!postRepository.existsById(id)) return ResponseEntity.notFound().build();
        postRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Post deleted"));
    }

    @DeleteMapping("/{postId}/replies/{replyId}")
    public ResponseEntity<Map<String, String>> deleteReply(@PathVariable Long postId,
                                                           @PathVariable Long replyId) {
        if (!replyRepository.existsById(replyId)) return ResponseEntity.notFound().build();
        replyRepository.deleteById(replyId);
        return ResponseEntity.ok(Map.of("message", "Reply deleted"));
    }

    record PostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String body,
        @Size(max = 100) String displayName
    ) {}

    record ReplyRequest(
        @NotBlank @Size(max = 2000) String body,
        @Size(max = 100) String displayName
    ) {}
}
