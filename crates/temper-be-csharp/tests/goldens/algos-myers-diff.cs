using System;
using System.Collections;
using System.Collections.Generic;
using TemperLang.Core;

namespace MyersDiff;

public static class MyersDiffGlobal
{
    public static readonly string ChangeTypeAddition;
    public static readonly string ChangeTypeDeletion;
    public static readonly string ChangeTypeUnchanged;

    public static string FormatPatch(Patch<string> patch)
    {
        string formatted = "";
        IReadOnlyList<Change<string>> changes = patch.Changes;
        int n = changes.Count;
        {
            int i = 0;
            while (i < n)
            {
                Change<string> change = Listed.Get(changes, i);
                string changeType = change.Type;
                IReadOnlyList<string> items = change.Items;
                string prefix = changeType == ChangeTypeAddition ? "+" : changeType == ChangeTypeDeletion ? "-" : " ";
                int nItems = items.Count;
                {
                    int j = 0;
                    while (j < nItems)
                    {
                        string item = Listed.Get(items, j);
                        formatted = formatted + prefix + item + "\n";
                        j = j + 1;
                    }
                }
                i = i + 1;
            }
        }
        return formatted;
    }

    public static Patch<T> Diff<T>(IReadOnlyList<T> left, IReadOnlyList<T> right, Func<T, T, bool> eq)
    {
        int leftLength = left.Count;
        int rightLength = right.Count;
        int reachedRowLength = leftLength + 1;
        int reachedColLength = rightLength + 1;
        BitArray reached = new BitArray(Math.Max(reachedRowLength * reachedColLength, 0));
        Queue<DiffPath?> costZeroEdges = new Queue<DiffPath?>();
        costZeroEdges.Enqueue(null);
        Queue<DiffPath> costOneEdges = new Queue<DiffPath>();
        while (true)
        {
            DiffPath? diffPath = costZeroEdges.Count != 0 ? Core.QueueRemoveFirst(costZeroEdges) : costOneEdges.Count != 0 ? Core.QueueRemoveFirst(costOneEdges) : null;
            int leftIndex;
            int? subject = diffPath == null ? null : (int?)diffPath.LeftIndex;
            leftIndex = subject == null ? 0 : subject.Value;
            int rightIndex;
            int? subject2 = diffPath == null ? null : (int?)diffPath.RightIndex;
            rightIndex = subject2 == null ? 0 : subject2.Value;
            if (leftIndex == leftLength && rightIndex == rightLength)
            {
                IList<DiffPath> pathElements = new List<DiffPath>();
                {
                    DiffPath? pathElement = diffPath;
                    while (pathElement != null)
                    {
                        DiffPath path = Core.CastToNonNull<DiffPath>(pathElement);
                        pathElements.Add(path);
                        pathElement = path.Previous;
                    }
                }
                Listed.Reverse(pathElements);
                int leftPatchIndex = 0;
                int rightPatchIndex = 0;
                IList<Change<T>> changes = new List<Change<T>>();
                int n = pathElements.Count;
                {
                    int i = 0;
                    int end;
                    while (i < n)
                    {
                        string changeType = Listed.Get(pathElements, i).ChangeType;
                        end = i + 1;
                        while (end < n && changeType == Listed.Get(pathElements, end).ChangeType)
                        {
                            end = end + 1;
                        }
                        int nItems = end - i;
                        IReadOnlyList<T> items;
                        int nLeftItems = 0;
                        int nRightItems = 0;
                        if (changeType == ChangeTypeAddition)
                        {
                            items = Listed.Slice(right, rightPatchIndex, rightPatchIndex + nItems);
                            nRightItems = nItems;
                        }
                        else
                        {
                            items = Listed.Slice(left, leftPatchIndex, leftPatchIndex + nItems);
                            if (changeType == ChangeTypeUnchanged)
                            {
                                nRightItems = nItems;
                            }
                            nLeftItems = nItems;
                        }
                        changes.Add(new Change<T>(changeType, leftPatchIndex, rightPatchIndex, items));
                        leftPatchIndex = leftPatchIndex + nLeftItems;
                        rightPatchIndex = rightPatchIndex + nRightItems;
                        i = end;
                    }
                }
                return new Patch<T>(Listed.ToReadOnlyList(changes));
            }
            if (leftIndex < leftLength)
            {
                if (rightIndex < rightLength)
                {
                    int sameReachedIndex = leftIndex + 1 + (rightIndex + 1) * reachedRowLength;
                    if (!Core.BitGet(reached, sameReachedIndex) && eq(Listed.Get(left, leftIndex), Listed.Get(right, rightIndex)))
                    {
                        Core.BitSet(reached, sameReachedIndex, true);
                        costZeroEdges.Enqueue(new DiffPath(leftIndex + 1, rightIndex + 1, ChangeTypeUnchanged, diffPath));
                    }
                }
                int delReachedIndex = leftIndex + 1 + rightIndex * reachedRowLength;
                if (!Core.BitGet(reached, delReachedIndex))
                {
                    Core.BitSet(reached, delReachedIndex, true);
                    costOneEdges.Enqueue(new DiffPath(leftIndex + 1, rightIndex, ChangeTypeDeletion, diffPath));
                }
            }
            if (rightIndex < rightLength)
            {
                int addReachedIndex = leftIndex + (rightIndex + 1) * reachedRowLength;
                if (!Core.BitGet(reached, addReachedIndex))
                {
                    Core.BitSet(reached, addReachedIndex, true);
                    costOneEdges.Enqueue(new DiffPath(leftIndex, rightIndex + 1, ChangeTypeAddition, diffPath));
                }
            }
        }
    }

    static MyersDiffGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("MyersDiff");
        ChangeTypeAddition = "Addition";
        ChangeTypeDeletion = "Deletion";
        ChangeTypeUnchanged = "Unchanged";
        console.Log(FormatPatch(Diff(Core.Split("Hello,\nWorld!", "\n"), Core.Split("Hello,\nCosmos!", "\n"), (string a, string b) => a == b)));
    }
}
