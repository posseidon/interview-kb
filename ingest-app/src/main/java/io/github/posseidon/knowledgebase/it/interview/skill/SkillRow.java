package io.github.posseidon.knowledgebase.it.interview.skill;

/** One parsed, not-yet-persisted row from the skill-tree import spreadsheet. */
record SkillRow(String name, String path, String description, Integer positionCount,
                String novice, String intermediate, String advanced, String expert) {

    private static final String PATH_SEPARATOR = " -> ";

    int depth() {
        return (int) path.chars().filter(c -> c == '>').count();
    }

    String parentPath() {
        int idx = path.lastIndexOf(PATH_SEPARATOR);
        return idx < 0 ? null : path.substring(0, idx);
    }
}
