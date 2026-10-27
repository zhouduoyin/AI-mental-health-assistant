package com.example.rag.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 仅用于 demo/单元测试的本地 Embedding 模型：把文本做「分词 + 哈希 + L2 归一化」，
 * 不依赖任何外部 API，向量维度可配置。
 *
 * <p>它不是语义模型，只保证“字面相近的文本向量相近”，用来在没有 API Key、没有 Milvus 时
 * 也能把「入库 → 检索 → 问答」整条链路跑通。生产环境请换成 OpenAI / 百炼 / Ollama 等真实
 * Embedding 模型。</p>
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    private final int dimensions;

    public HashingEmbeddingModel(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions 必须大于 0");
        }
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(instructions.size());
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(embedText(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embedText(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    public float[] embedText(String text) {
        float[] vector = new float[dimensions];
        if (text == null || text.isBlank()) {
            vector[0] = 1.0f;
            return vector;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (CJK.matcher(token).find()) {
                addUnigramsAndBigrams(vector, token);
            } else {
                addToken(vector, token);
            }
        }
        normalize(vector);
        return vector;
    }

    private void addUnigramsAndBigrams(float[] vector, String token) {
        for (int i = 0; i < token.length(); i++) {
            addToken(vector, String.valueOf(token.charAt(i)));
        }
        for (int i = 0; i + 1 < token.length(); i++) {
            addToken(vector, token.substring(i, i + 2));
        }
    }

    private void addToken(float[] vector, String token) {
        int hash = token.hashCode();
        int index = Math.floorMod(hash ^ (hash >>> 16), dimensions);
        vector[index] += 1.0f;
    }

    private void normalize(float[] vector) {
        double sum = 0.0d;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum == 0.0d) {
            vector[0] = 1.0f;
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
