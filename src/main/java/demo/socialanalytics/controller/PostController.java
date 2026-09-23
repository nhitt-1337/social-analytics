package demo.socialanalytics.controller;

import demo.socialanalytics.dto.request.PageQuery;
import demo.socialanalytics.dto.request.PostRequest;
import demo.socialanalytics.dto.response.PageResponse;
import demo.socialanalytics.dto.response.PostResponse;
import demo.socialanalytics.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Posts", description = "Quản lý bài viết cần theo dõi tương tác")
@RestController
@RequestMapping("/posts")
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Operation(summary = "Danh sách bài viết", description = "Lọc theo platform (facebook|twitter), phân trang ở DB")
    @GetMapping
    public ResponseEntity<PageResponse<PostResponse>> list(
        @RequestParam(required = false) String platform,
        @Valid PageQuery pageQuery
    ) {
        return ResponseEntity.ok(
            postService.list(platform, pageQuery.pageOrDefault(), pageQuery.limitOrDefault()));
    }

    @Operation(summary = "Chi tiết bài viết kèm số liệu mới nhất")
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getById(id));
    }

    @Operation(summary = "Tạo bài viết")
    @PostMapping
    public ResponseEntity<PostResponse> create(@Valid @RequestBody PostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.create(request));
    }

    @Operation(summary = "Cập nhật bài viết")
    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> update(@PathVariable Long id, @Valid @RequestBody PostRequest request) {
        return ResponseEntity.ok(postService.update(id, request));
    }

    @Operation(summary = "Xoá bài viết", description = "Xoá kèm toàn bộ lịch sử chỉ số của bài")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
