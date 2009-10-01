package org.navalplanner.web.orders;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.navalplanner.business.labels.daos.ILabelDAO;
import org.navalplanner.business.labels.daos.ILabelTypeDAO;
import org.navalplanner.business.labels.entities.Label;
import org.navalplanner.business.labels.entities.LabelType;
import org.navalplanner.business.orders.daos.IOrderElementDAO;
import org.navalplanner.business.orders.entities.OrderElement;
import org.navalplanner.business.orders.entities.OrderLineGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Diego Pino Garcia <dpino@igalia.com>
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class AssignedLabelsToOrderElementModel implements
        IAssignedLabelsToOrderElementModel {

    @Autowired
    IOrderElementDAO orderDAO;

    @Autowired
    ILabelTypeDAO labelTypeDAO;

    @Autowired
    ILabelDAO labelDAO;

    OrderElement orderElement;

    @Override
    @Transactional(readOnly = true)
    public void init(OrderElement orderElement) {
        // orderDAO.save(orderElement);
        this.orderElement = orderElement;
        reattachOrderElement(this.orderElement);
    }

    private void reattachOrderElement(OrderElement orderElement) {
        orderDAO.save(orderElement);
        orderElement.getName();
        if (orderElement.getParent() != null) {
            orderElement.getParent().getName();
        }
        reattachLabels(orderElement.getLabels());
    }

    private void reattachLabels(Set<Label> labels) {
        for (Label label : labels) {
            label.getName();
            label.getType().getName();
        }
    }

    @Transactional(readOnly = true)
    public List<Label> getLabels() {
        List<Label> result = new ArrayList<Label>();
        if (orderElement != null && orderElement.getLabels() != null) {
            result.addAll(orderElement.getLabels());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public boolean existsLabelByNameAndType(String labelName,
            LabelType labelType) {
        return (labelDAO.findByNameAndType(labelName, labelType) != null);
    }

    public void addLabel(String labelName, LabelType labelType) {
        Label label = Label.create(labelName);
        label.setType(labelType);
        orderElement.addLabel(label);
    }

    @Override
    public void deleteLabel(Label label) {
        orderElement.removeLabel(label);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Label> getInheritedLabels() {
        System.out.println("### getInheritedLabels");

        List<Label> result = new ArrayList<Label>();

        if (orderElement != null) {
            OrderLineGroup parent = orderElement.getParent();
            while (parent != null) {
                reattachOrderElement(parent);
                // System.out.println("### labels: " + parent.getLabels());
                result.addAll(parent.getLabels());
                parent = parent.getParent();
            }
        }
        return result;
    }

    @Override
    public OrderElement getOrderElement() {
        return orderElement;
    }

    @Override
    public void setOrderElement(OrderElement orderElement) {
        this.orderElement = orderElement;
    }

}
