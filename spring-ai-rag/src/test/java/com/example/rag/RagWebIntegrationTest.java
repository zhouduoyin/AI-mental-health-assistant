package com.example.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 层集成测试：入库 → 检索 → 删除向量，覆盖 H2 + SimpleVectorStore。
 */
@SpringBootTest(properties = "rag.demo.seed=false")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class RagWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("文本入库、文件入库、检索、删除全流程")
    void documentSearchAndDeleteFlow() throws Exception {
        String body = """
                {"title":"Milvus 向量检索说明","source":"test",
                 "content":"Milvus 是开源向量数据库，支持 IVF_FLAT、HNSW 等索引类型，适合做 RAG 检索。"}""";

        MvcResult ingestResult = mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.chunkCount").value(greaterThan(0)))
                .andReturn();
        JsonNode ingestJson = objectMapper.readTree(ingestResult.getResponse().getContentAsString());
        int docId = ingestJson.get("id").asInt();

        MockMultipartFile file = new MockMultipartFile("file", "notes.md", "text/markdown",
                "Spring Boot 项目使用 MySQL 保存业务数据，使用 Spring AI 调用大模型。"
                        .getBytes(StandardCharsets.UTF_8));
        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload").file(file).param("title", "上传的 Markdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("上传的 Markdown"))
                .andReturn();
        int uploadedDocId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asInt();

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"HNSW 索引\",\"topK\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].docId").value(docId))
                .andExpect(jsonPath("$[0].title").value("Milvus 向量检索说明"));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/documents/" + docId))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/documents/" + uploadedDocId))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"HNSW 索引\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(0));
    }
}
