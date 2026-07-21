package com.ohtoai.mobeditor.mode;

/**
 * Four operation modes for the MobEditor tool.
 */
public enum EditorMode {
    /** Open entity equipment editing GUI */
    OPEN_EQUIPMENT,
    /** Copy entity equipment to clipboard */
    COPY,
    /** Paste clipboard to entity */
    PASTE,
    /** Extract entity into a spawn egg */
    EXTRACT_EGG;

    public String getDisplayName() {
        return switch (this) {
            case OPEN_EQUIPMENT -> "编辑装备";
            case COPY -> "复制装备";
            case PASTE -> "粘贴装备";
            case EXTRACT_EGG -> "提取刷怪蛋";
        };
    }
}
