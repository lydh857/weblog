package com.blog.infra.security.sensitive;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SensitiveWordService {

    private static final char MASK_CHAR = '*';

    private final ResourceLoader resourceLoader;

    @Value("${blog.security.sensitive-words.location:classpath:sensitive/sensi_words.txt}")
    private String wordsLocation;

    private volatile TrieNode root = new TrieNode();

    public SensitiveWordService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        List<String> words = new ArrayList<>(loadWordSet(wordsLocation));
        this.root = buildTrie(words);
        log.info("敏感词库加载完成: location={}, words={}", wordsLocation, words.size());
    }

    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        TrieNode trieRoot = root;
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (longestMatchLength(chars, i, trieRoot) > 0) {
                return true;
            }
        }
        return false;
    }

    public String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        TrieNode trieRoot = root;
        char[] chars = text.toCharArray();
        boolean[] masked = new boolean[chars.length];
        boolean hasMatch = false;

        for (int i = 0; i < chars.length; i++) {
            int matchLen = longestMatchLength(chars, i, trieRoot);
            if (matchLen <= 0) {
                continue;
            }
            hasMatch = true;
            for (int j = i; j < i + matchLen; j++) {
                masked[j] = true;
            }
            i += matchLen - 1;
        }

        if (!hasMatch) {
            return text;
        }

        StringBuilder builder = new StringBuilder(text);
        for (int i = 0; i < builder.length(); i++) {
            if (masked[i]) {
                builder.setCharAt(i, MASK_CHAR);
            }
        }
        return builder.toString();
    }

    private LinkedHashSet<String> loadWordSet(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.warn("敏感词文件不存在，使用内置默认词库: location={}", location);
            return new LinkedHashSet<>(getDefaultWords());
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            LinkedHashSet<String> words = readWordLines(reader);

            if (words.isEmpty()) {
                log.warn("敏感词文件为空，使用内置默认词库: location={}", location);
                return new LinkedHashSet<>(getDefaultWords());
            }

            return words;
        } catch (Exception e) {
            log.error("读取敏感词文件失败，使用内置默认词库: location={}", location, e);
            return new LinkedHashSet<>(getDefaultWords());
        }
    }

    private LinkedHashSet<String> readWordLines(BufferedReader reader) throws Exception {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String normalized = normalizeWord(line);
            if (normalized != null) {
                words.add(normalized);
            }
        }
        return words;
    }

    private String normalizeWord(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String word = rawLine.replace("\uFEFF", "").trim();
        if (word.isEmpty()) {
            return null;
        }
        if (word.startsWith("#")) {
            return null;
        }
        return word;
    }

    private TrieNode buildTrie(List<String> words) {
        TrieNode rootNode = new TrieNode();
        for (String word : words) {
            TrieNode node = rootNode;
            for (char ch : word.toCharArray()) {
                node = node.children.computeIfAbsent(ch, key -> new TrieNode());
            }
            node.end = true;
        }
        return rootNode;
    }

    private int longestMatchLength(char[] chars, int start, TrieNode trieRoot) {
        TrieNode node = trieRoot;
        int longest = 0;
        for (int i = start; i < chars.length; i++) {
            node = node.children.get(chars[i]);
            if (node == null) {
                break;
            }
            if (node.end) {
                longest = i - start + 1;
            }
        }
        return longest;
    }

    private List<String> getDefaultWords() {
        return List.of(
                "赌博",
                "色情",
                "毒品",
                "枪支",
                "炸弹"
        );
    }

    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private boolean end;
    }
}
