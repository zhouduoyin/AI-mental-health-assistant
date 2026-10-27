package com.example.rag.web;

import com.example.rag.domain.UserAccount;
import com.example.rag.service.AuthTokenService;
import com.example.rag.service.MoodService;
import com.example.rag.web.dto.MoodRequest;
import com.example.rag.web.dto.MoodView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台情绪记录：只能读写当前登录用户自己的数据。
 */
@RestController
@RequestMapping("/api/moods")
@Tag(name = "情绪记录", description = "当前用户每日情绪打卡")
public class MoodController {

    private final MoodService moodService;
    private final AuthTokenService tokenService;

    public MoodController(MoodService moodService, AuthTokenService tokenService) {
        this.moodService = moodService;
        this.tokenService = tokenService;
    }

    @GetMapping
    @Operation(summary = "我的情绪记录")
    public List<MoodView> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserAccount user = tokenService.require(authorization);
        return moodService.listMine(user);
    }

    @PostMapping
    @Operation(summary = "保存某天情绪（同一天覆盖更新）")
    public MoodView save(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @Valid @RequestBody MoodRequest request) {
        UserAccount user = tokenService.require(authorization);
        return moodService.save(user, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除自己的某条情绪记录")
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @PathVariable Long id) {
        UserAccount user = tokenService.require(authorization);
        moodService.deleteMine(user, id);
    }
}
