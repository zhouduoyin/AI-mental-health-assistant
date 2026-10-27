package com.example.rag.web;

import com.example.rag.service.AuthTokenService;
import com.example.rag.service.MoodService;
import com.example.rag.web.dto.MoodView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/moods")
@Tag(name = "后台情绪日志", description = "查看与删除全部用户情绪记录，需要后台账号")
public class AdminMoodController {

    private final MoodService moodService;
    private final AuthTokenService tokenService;

    public AdminMoodController(MoodService moodService, AuthTokenService tokenService) {
        this.moodService = moodService;
        this.tokenService = tokenService;
    }

    @GetMapping
    @Operation(summary = "全部情绪记录")
    public List<MoodView> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        tokenService.requireBack(authorization);
        return moodService.listAll();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除情绪记录")
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @PathVariable Long id) {
        tokenService.requireBack(authorization);
        moodService.deleteAny(id);
    }
}
