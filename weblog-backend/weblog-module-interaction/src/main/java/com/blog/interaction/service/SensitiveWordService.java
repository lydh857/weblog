package com.blog.interaction.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 敏感词过滤服务（DFA 算法）
 * - 构建字典树（Trie）
 * - 支持动态加载敏感词
 * - 支持替换为 ***
 */
@Slf4j
@Service
public class SensitiveWordService {

    /** DFA 状态机根节点 */
    private Map<Character, Object> dfaMap = new HashMap<>();

    /** 结束标记 */
    private static final String END_FLAG = "isEnd";

    @PostConstruct
    public void init() {
        // 初始化默认敏感词（实际项目可从数据库/文件加载）
        List<String> words = getDefaultSensitiveWords();
        buildDFA(words);
        log.info("敏感词库初始化完成，共 {} 个词", words.size());
    }

    /**
     * 检测文本是否包含敏感词
     */
    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            int len = checkSensitiveWord(text, i);
            if (len > 0) return true;
        }
        return false;
    }

    /**
     * 替换敏感词为 ***
     */
    public String filter(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder result = new StringBuilder(text);
        for (int i = 0; i < result.length(); i++) {
            int len = checkSensitiveWord(result.toString(), i);
            if (len > 0) {
                for (int j = i; j < i + len; j++) {
                    result.setCharAt(j, '*');
                }
                i += len - 1;
            }
        }
        return result.toString();
    }

    /**
     * 动态添加敏感词
     */
    public void addWords(List<String> words) {
        buildDFA(words);
        log.info("新增敏感词 {} 个", words.size());
    }

    /**
     * 重建敏感词库
     */
    public void rebuild(List<String> words) {
        dfaMap = new HashMap<>();
        buildDFA(words);
        log.info("敏感词库重建完成，共 {} 个词", words.size());
    }

    // ========== DFA 核心 ==========

    @SuppressWarnings("unchecked")
    private void buildDFA(List<String> words) {
        for (String word : words) {
            if (word == null || word.trim().isEmpty()) continue;
            Map<Character, Object> current = dfaMap;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                Object node = current.get(c);
                if (node == null) {
                    Map<Character, Object> newNode = new HashMap<>();
                    current.put(c, newNode);
                    current = newNode;
                } else {
                    current = (Map<Character, Object>) node;
                }
            }
            current.put(END_FLAG.charAt(0), END_FLAG);
        }
    }

    @SuppressWarnings("unchecked")
    private int checkSensitiveWord(String text, int startIndex) {
        Map<Character, Object> current = dfaMap;
        int matchLen = 0;
        int lastMatchLen = 0;

        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            Object node = current.get(c);
            if (node == null) break;

            matchLen++;
            current = (Map<Character, Object>) node;

            if (current.containsKey(END_FLAG.charAt(0))) {
                lastMatchLen = matchLen;
            }
        }
        return lastMatchLen;
    }

    private List<String> getDefaultSensitiveWords() {
        // 基础敏感词列表（示例，实际应从配置/数据库加载）
        return List.of(
                "傻逼", "操你", "去死", "白痴", "废物",
                "赌博", "色情", "毒品", "枪支", "炸弹"
        );
    }
}
