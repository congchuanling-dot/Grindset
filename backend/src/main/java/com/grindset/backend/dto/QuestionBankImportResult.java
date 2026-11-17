package com.grindset.backend.dto;

/** 题库导入结果:新增条数与被跳过的重复条数。 */
public record QuestionBankImportResult(int imported, int skipped) {
}
