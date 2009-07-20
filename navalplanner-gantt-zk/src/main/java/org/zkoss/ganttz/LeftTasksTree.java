package org.zkoss.ganttz;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.zkoss.ganttz.LeftTasksTreeRow.ILeftTasksTreeNavigator;
import org.zkoss.ganttz.data.TaskBean;
import org.zkoss.ganttz.data.TaskContainerBean;
import org.zkoss.ganttz.util.MutableTreeModel;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.HtmlMacroComponent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.OpenEvent;
import org.zkoss.zul.Tree;
import org.zkoss.zul.TreeModel;
import org.zkoss.zul.Treecell;
import org.zkoss.zul.Treeitem;
import org.zkoss.zul.TreeitemRenderer;

public class LeftTasksTree extends HtmlMacroComponent {

    private final class TaskBeanRenderer implements TreeitemRenderer {
        public void render(Treeitem item, Object data) throws Exception {
            TaskBean taskBean = (TaskBean) data;
            item.setOpen(isOpened(taskBean));
            final int[] path = tasksTreeModel.getPath(tasksTreeModel.getRoot(),
                    taskBean);
            String cssClass = "depth_" + path.length;
            LeftTasksTreeRow leftTasksTreeRow = LeftTasksTreeRow.create(taskBean,
                    new TreeNavigator(tasksTreeModel, taskBean));
            if (taskBean.isContainer()) {
                expandWhenOpened((TaskContainerBean) taskBean, item);
            }
            Component row = Executions.getCurrent().createComponents(
                    "~./ganttz/zul/leftTasksTreeRow.zul", item, null);
            leftTasksTreeRow.doAfterCompose(row);
            List<Object> rowChildren = row.getChildren();
            List<Treecell> treeCells = Planner.findComponentsOfType(
                    Treecell.class, rowChildren);
            for (Treecell cell : treeCells) {
                cell.setSclass(cssClass);
            }
            detailsForBeans.put(taskBean, leftTasksTreeRow);
        }

        private void expandWhenOpened(final TaskContainerBean taskBean,
                Treeitem item) {
            item.addEventListener("onOpen", new EventListener() {
                @Override
                public void onEvent(Event event) throws Exception {
                    OpenEvent openEvent = (OpenEvent) event;
                    taskBean.setExpanded(openEvent.isOpen());
                }
            });
        }

    }

    public boolean isOpened(TaskBean taskBean) {
        return taskBean.isLeaf() || taskBean.isExpanded();
    }

    private final class DetailsForBeans {
        private Map<TaskBean, LeftTasksTreeRow> map = new HashMap<TaskBean, LeftTasksTreeRow>();

        private Set<TaskBean> focusRequested = new HashSet<TaskBean>();

        public void put(TaskBean taskBean, LeftTasksTreeRow leftTasksTreeRow) {
            map.put(taskBean, leftTasksTreeRow);
            if (focusRequested.contains(taskBean)) {
                focusRequested.remove(taskBean);
                leftTasksTreeRow.receiveFocus();
            }
        }

        public void requestFocusFor(TaskBean taskBean) {
            focusRequested.add(taskBean);
        }

        public LeftTasksTreeRow get(TaskBean taskbean) {
            return map.get(taskbean);
        }

    }

    private DetailsForBeans detailsForBeans = new DetailsForBeans();

    private final class TreeNavigator implements ILeftTasksTreeNavigator {
        private final int[] pathToNode;
        private final TaskBean task;

        private TreeNavigator(TreeModel treemodel, TaskBean task) {
            this.task = task;
            this.pathToNode = tasksTreeModel.getPath(tasksTreeModel.getRoot(),
                    task);
        }

        @Override
        public LeftTasksTreeRow getAboveRow() {
            TaskBean parent = getParent(pathToNode);
            int lastPosition = pathToNode[pathToNode.length - 1];
            if (lastPosition != 0) {
                return getChild(parent, lastPosition - 1);
            } else if (tasksTreeModel.getRoot() != parent) {
                return getDetailFor(parent);
            }
            return null;
        }

        private LeftTasksTreeRow getChild(TaskBean parent, int position) {
            TaskBean child = tasksTreeModel.getChild(parent, position);
            return getDetailFor(child);
        }

        private LeftTasksTreeRow getDetailFor(TaskBean child) {
            return detailsForBeans.get(child);
        }

        @Override
        public LeftTasksTreeRow getBelowRow() {
            if (isExpanded() && hasChildren()) {
                return getChild(task, 0);
            }
            for (ChildAndParent childAndParent : group(task, tasksTreeModel
                    .getParents(task))) {
                if (childAndParent.childIsNotLast()) {
                    return getDetailFor(childAndParent.getNextToChild());
                }
            }
            // it's the last one, it has none below
            return null;
        }

        public List<ChildAndParent> group(TaskBean origin,
                List<TaskBean> parents) {
            ArrayList<ChildAndParent> result = new ArrayList<ChildAndParent>();
            TaskBean child = origin;
            TaskBean parent;
            ListIterator<TaskBean> listIterator = parents.listIterator();
            while (listIterator.hasNext()) {
                parent = listIterator.next();
                result.add(new ChildAndParent(child, parent));
                child = parent;
            }
            return result;
        }

        private class ChildAndParent {
            private final TaskBean parent;

            private final TaskBean child;

            private Integer positionOfChildCached;

            private ChildAndParent(TaskBean child, TaskBean parent) {
                this.parent = parent;
                this.child = child;
            }

            public TaskBean getNextToChild() {
                return tasksTreeModel
                        .getChild(parent, getPositionOfChild() + 1);
            }

            public boolean childIsNotLast() {
                return getPositionOfChild() < numberOfChildrenForParent() - 1;
            }

            private int numberOfChildrenForParent() {
                return tasksTreeModel.getChildCount(parent);
            }

            private int getPositionOfChild() {
                if (positionOfChildCached != null)
                    return positionOfChildCached;
                int[] path = tasksTreeModel.getPath(parent, child);
                return positionOfChildCached = path[path.length - 1];
            }
        }

        private boolean hasChildren() {
            return task.isContainer() && task.getTasks().size() > 0;
        }

        private boolean isExpanded() {
            return task.isContainer() && task.isExpanded();
        }

        private TaskBean getParent(int[] path) {
            TaskBean current = tasksTreeModel.getRoot();
            for (int i = 0; i < path.length - 1; i++) {
                current = tasksTreeModel.getChild(current, path[i]);
            }
            return current;
        }

    }

    private static Log LOG = LogFactory.getLog(LeftTasksTree.class);

    private TaskRemovedListener taskRemovedListener;

    private final List<TaskBean> taskBeans;

    private MutableTreeModel<TaskBean> tasksTreeModel;

    private Tree tasksTree;

    private CommandContextualized<?> goingDownInLastArrowCommand;

    public LeftTasksTree(List<TaskBean> taskBeans) {
        this.taskBeans = taskBeans;
    }

    private static void fillModel(MutableTreeModel<TaskBean> treeModel,
            List<TaskBean> taskBeans) {
        for (TaskBean taskBean : taskBeans) {
            fillModel(treeModel, treeModel.getRoot(), taskBean);
        }
    }

    private static void fillModel(MutableTreeModel<TaskBean> treeModel,
            TaskBean parent, TaskBean node) {
        treeModel.add(parent, node);
        if (node.isContainer()) {
            for (TaskBean child : node.getTasks()) {
                fillModel(treeModel, node, child);
            }
        }
    }

    Planner getPlanner() {
        return (Planner) getParent();
    }

    public void taskRemoved(TaskBean taskRemoved) {
        tasksTreeModel.remove(taskRemoved);
    }

    private static Date threeMonthsLater(Date now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.MONTH, 3);
        return calendar.getTime();
    }

    @Override
    public void afterCompose() {
        setClass("listdetails");
        super.afterCompose();
        tasksTree = (Tree) getFellow("tasksTree");
        tasksTreeModel = MutableTreeModel.create(TaskBean.class);
        fillModel(tasksTreeModel, taskBeans);
        tasksTree.setModel(tasksTreeModel);
        tasksTree.setTreeitemRenderer(new TaskBeanRenderer());
    }

    void addTask(TaskBean taskBean) {
        detailsForBeans.requestFocusFor(taskBean);
        tasksTreeModel.add(tasksTreeModel.getRoot(), taskBean);
    }

    public CommandContextualized<?> getGoingDownInLastArrowCommand() {
        return goingDownInLastArrowCommand;
    }

    public void setGoingDownInLastArrowCommand(
            CommandContextualized<?> goingDownInLastArrowCommand) {
        this.goingDownInLastArrowCommand = goingDownInLastArrowCommand;
    }

}
