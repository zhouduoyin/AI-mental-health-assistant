package com.example.rag.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashingEmbeddingModelTest {

    private final HashingEmbeddingModel model = new HashingEmbeddingModel(256);

    @Test
    void embeddingIsDeterministicAndNormalized() {
        float[] first = model.embedText("Spring AI 向量数据库 Milvus");
        float[] second = model.embedText("Spring AI 向量数据库 Milvus");

        assertThat(first).hasSize(256).containsExactly(second);
        double norm = 0.0d;
        for (float value : first) {
            norm += value * value;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0d, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    void similarTextScoresHigherThanUnrelatedText() {
        float[] query = model.embedText("向量数据库 Milvus");
        float[] related = model.embedText("Milvus 是一个开源向量数据库");
        float[] unrelated = model.embedText("今天天气很好适合出去散步");

        assertThat(cosine(query, related)).isGreaterThan(cosine(query, unrelated));
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0.0d;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}
