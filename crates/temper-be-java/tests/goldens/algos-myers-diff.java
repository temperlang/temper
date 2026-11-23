package myers_diff;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class MyersDiffGlobal {
    private MyersDiffGlobal() {}

    public static final String ChangeTypeAddition;
    public static final String ChangeTypeDeletion;
    public static final String ChangeTypeUnchanged;

    public static String formatPatch(Patch<String> patch) {
        String formatted = "";
        List<Change<String>> changes = patch.changes;
        int n = changes.size();
        {
            int i = 0;
            while (i < n) {
                Change<String> change = Core.listGet(changes, i);
                String changeType = change.type;
                List<String> items = change.items;
                String prefix = changeType.equals(ChangeTypeAddition) ? "+" : changeType.equals(ChangeTypeDeletion) ? "-" : " ";
                int nItems = items.size();
                {
                    int j = 0;
                    while (j < nItems) {
                        String item = Core.listGet(items, j);
                        formatted = formatted + prefix + item + "\n";
                        j = j + 1;
                    }
                }
                i = i + 1;
            }
        }
        return formatted;
    }

    public static <T> Patch<T> diff(List<T> left, List<T> right, BiPredicate<T, T> eq) {
        int leftLength = left.size();
        int rightLength = right.size();
        int reachedRowLength = leftLength + 1;
        int reachedColLength = rightLength + 1;
        BitSet reached = new BitSet(Math.max(reachedRowLength * reachedColLength, 0));
        Deque<@Nullable DiffPath> costZeroEdges = new LinkedList<>();
        costZeroEdges.addLast(null);
        Deque<DiffPath> costOneEdges = new ArrayDeque<>();
        while (true) {
            @Nullable DiffPath diffPath = !costZeroEdges.isEmpty() ? Core.dequeRemoveFirst(costZeroEdges) : !costOneEdges.isEmpty() ? Core.dequeRemoveFirst(costOneEdges) : null;
            @Nullable Integer subject = diffPath == null ? null : Integer.valueOf(diffPath.leftIndex);
            int leftIndex = subject == null ? 0 : subject;
            @Nullable Integer subject2 = diffPath == null ? null : Integer.valueOf(diffPath.rightIndex);
            int rightIndex = subject2 == null ? 0 : subject2;
            if (leftIndex == leftLength && rightIndex == rightLength) {
                List<DiffPath> pathElements = new ArrayList<>();
                {
                    @Nullable DiffPath pathElement = diffPath;
                    while (pathElement != null) {
                        DiffPath path = Objects.requireNonNull(pathElement);
                        pathElements.add(path);
                        pathElement = path.previous;
                    }
                }
                Collections.reverse(pathElements);
                int leftPatchIndex = 0;
                int rightPatchIndex = 0;
                List<Change<T>> changes = new ArrayList<>();
                int n = pathElements.size();
                {
                    int i = 0;
                    int end;
                    while (i < n) {
                        String changeType = Core.listGet(pathElements, i).changeType;
                        end = i + 1;
                        while (end < n && changeType.equals(Core.listGet(pathElements, end).changeType)) {
                            end = end + 1;
                        }
                        int nItems = end - i;
                        List<T> items;
                        int nLeftItems = 0;
                        int nRightItems = 0;
                        if (changeType.equals(ChangeTypeAddition)) {
                            items = Core.listSlice(right, rightPatchIndex, rightPatchIndex + nItems);
                            nRightItems = nItems;
                        } else {
                            items = Core.listSlice(left, leftPatchIndex, leftPatchIndex + nItems);
                            if (changeType.equals(ChangeTypeUnchanged)) {
                                nRightItems = nItems;
                            }
                            nLeftItems = nItems;
                        }
                        changes.add(new Change<T>(changeType, leftPatchIndex, rightPatchIndex, items));
                        leftPatchIndex = leftPatchIndex + nLeftItems;
                        rightPatchIndex = rightPatchIndex + nRightItems;
                        i = end;
                    }
                }
                return new Patch<T>(List.copyOf(changes));
            }
            if (leftIndex < leftLength) {
                if (rightIndex < rightLength) {
                    int sameReachedIndex = leftIndex + 1 + (rightIndex + 1) * reachedRowLength;
                    if (!Core.bitVectorGet(reached, sameReachedIndex) && eq.test(Core.listGet(left, leftIndex), Core.listGet(right, rightIndex))) {
                        reached.set(sameReachedIndex, true);
                        costZeroEdges.addLast(new DiffPath(leftIndex + 1, rightIndex + 1, ChangeTypeUnchanged, diffPath));
                    }
                }
                int delReachedIndex = leftIndex + 1 + rightIndex * reachedRowLength;
                if (!Core.bitVectorGet(reached, delReachedIndex)) {
                    reached.set(delReachedIndex, true);
                    costOneEdges.addLast(new DiffPath(leftIndex + 1, rightIndex, ChangeTypeDeletion, diffPath));
                }
            }
            if (rightIndex < rightLength) {
                int addReachedIndex = leftIndex + (rightIndex + 1) * reachedRowLength;
                if (!Core.bitVectorGet(reached, addReachedIndex)) {
                    reached.set(addReachedIndex, true);
                    costOneEdges.addLast(new DiffPath(leftIndex, rightIndex + 1, ChangeTypeAddition, diffPath));
                }
            }
        }
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("myers_diff"));
        ChangeTypeAddition = "Addition";
        ChangeTypeDeletion = "Deletion";
        ChangeTypeUnchanged = "Unchanged";
        console.log(formatPatch(diff(Core.stringSplit("Hello,\nWorld!", "\n"), Core.stringSplit("Hello,\nCosmos!", "\n"), (a, b) -> a.equals(b))));
    }
}
