package com.grindset.backend.dto;

/**
 * 题库中某道题在默认排序下的前后相邻题,用于详情页连续刷题。
 * 到达边界时对应字段为 null。
 */
public record QuestionNeighbors(String prevId, String nextId) {
}
