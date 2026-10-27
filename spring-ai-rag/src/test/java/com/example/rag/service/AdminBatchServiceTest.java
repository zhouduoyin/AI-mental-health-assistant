package com.example.rag.service;

import com.example.rag.domain.UserAccount;
import com.example.rag.support.ResourceNotFoundException;
import com.example.rag.web.dto.BatchItemResult;
import com.example.rag.web.dto.BatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBatchServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private ArticleService articleService;

    @Mock
    private UserAccount operator;

    private AdminBatchService service;

    @BeforeEach
    void setUp() {
        service = new AdminBatchService(userService, articleService);
    }

    @Test
    @DisplayName("批量启停用户去重并保留部分成功结果")
    void changeUserStatusDeduplicatesAndReportsPartialFailure() {
        lenient().doThrow(new IllegalArgumentException("不能停用自己的账号"))
                .when(userService).changeStatus(2L, false, operator);

        BatchResult result = service.changeUserStatus(List.of(1L, 2L, 1L), false, operator);

        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.items())
                .extracting(BatchItemResult::id)
                .containsExactly(1L, 2L);
        assertThat(result.items().get(1).success()).isFalse();
        assertThat(result.items().get(1).message()).contains("不能停用");
        verify(userService, times(1)).changeStatus(1L, false, operator);
        verify(userService, times(1)).changeStatus(2L, false, operator);
    }

    @Test
    @DisplayName("批量删除用户跳过 null ID 并汇总不存在记录")
    void deleteUsersIgnoresNullsAndReportsMissingIds() {
        lenient().doThrow(new ResourceNotFoundException("用户不存在或已被删除"))
                .when(userService).delete(99L, operator);

        BatchResult result = service.deleteUsers(Arrays.asList(7L, null, 99L, 7L), operator);

        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.items().get(1).message()).contains("不存在");
        verify(userService, times(1)).delete(7L, operator);
        verify(userService, times(1)).delete(99L, operator);
    }

    @Test
    @DisplayName("批量文章状态非法时整体拒绝，不执行任何单条操作")
    void changeArticleStatusRejectsInvalidStatusBeforeProcessing() {
        assertThatThrownBy(() -> service.changeArticleStatus(List.of(1L), "不存在的状态"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已发布 / 草稿");

        verifyNoInteractions(articleService);
    }

    @Test
    @DisplayName("批量上下线文章逐项执行并保留失败明细")
    void changeArticleStatusReportsPartialFailure() {
        when(articleService.changeStatus(10L, "已发布")).thenReturn(null);
        lenient().doThrow(new ResourceNotFoundException("文章不存在：11"))
                .when(articleService).changeStatus(11L, "已发布");

        BatchResult result = service.changeArticleStatus(List.of(10L, 11L), "已发布");

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.items().get(1).message()).contains("11");
    }

    @Test
    @DisplayName("批量操作超过 100 项时拒绝，避免单请求占用过多资源")
    void rejectsOversizedBatch() {
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();

        assertThatThrownBy(() -> service.deleteArticles(ids))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");

        verify(articleService, never()).delete(any());
        verifyNoInteractions(userService);
    }
}
