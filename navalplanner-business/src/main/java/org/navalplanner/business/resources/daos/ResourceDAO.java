/*
 * This file is part of NavalPlan
 *
 * Copyright (C) 2009 Fundación para o Fomento da Calidade Industrial e
 *                    Desenvolvemento Tecnolóxico de Galicia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.business.resources.daos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.Validate;
import org.hibernate.Query;
import org.hibernate.criterion.Restrictions;
import org.navalplanner.business.common.daos.IntegrationEntityDAO;
import org.navalplanner.business.planner.entities.Task;
import org.navalplanner.business.resources.entities.Criterion;
import org.navalplanner.business.resources.entities.LimitingResourceQueue;
import org.navalplanner.business.resources.entities.Machine;
import org.navalplanner.business.resources.entities.Resource;
import org.navalplanner.business.resources.entities.Worker;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hibernate DAO for the <code>Resource</code> entity.
 * @author Fernando Bellas Permuy <fbellas@udc.es>
 * @author Diego Pino Garcia <dpino@udc.es>
 */
@Repository
@Scope(BeanDefinition.SCOPE_SINGLETON)
@Transactional
public class ResourceDAO extends IntegrationEntityDAO<Resource> implements
    IResourceDAO {

    @Override
    public List<Worker> getWorkers() {
        return list(Worker.class);
    }

    @Override
    public List<Worker> getVirtualWorkers() {
        List<Worker> list = getWorkers();
        for (Iterator<Worker> iterator = list.iterator(); iterator.hasNext();) {
            Worker worker = iterator.next();
            if (worker.isReal()) {
                iterator.remove();
            }
        }
        return list;
    }

    @Override
    public List<Worker> getRealWorkers() {
        List<Worker> list = getWorkers();
        for (Iterator<Worker> iterator = list.iterator(); iterator.hasNext();) {
            Worker worker = iterator.next();
            if (worker.isVirtual()) {
                iterator.remove();
            }
        }
        return list;
    }

    @Override
    public List<Resource> findSatisfyingCriterionsAtSomePoint(
            Collection<? extends Criterion> criterions) {
        Validate.notNull(criterions);
        Validate.noNullElements(criterions);
        if (criterions.isEmpty()) {
            return list(Resource.class);
        }
        return findRelatedWithSomeOfTheCriterions(criterions);
    }

    @SuppressWarnings("unchecked")
    private List<Resource> findRelatedWithSomeOfTheCriterions(
            Collection<? extends Criterion> criterions) {
        String strQuery = "SELECT DISTINCT resource "
                + "FROM Resource resource "
                + "LEFT OUTER JOIN resource.criterionSatisfactions criterionSatisfactions "
                + "LEFT OUTER JOIN criterionSatisfactions.criterion criterion "
                + "WHERE criterion IN (:criterions)";
        Query query = getSession().createQuery(strQuery);
        query.setParameterList("criterions", criterions);
        return (List<Resource>) query.list();
    }

    @Override
    public List<Resource> findSatisfyingAllCriterions(
            Collection<? extends Criterion> criteria,
            boolean limitingResource) {

        return selectSatisfiyingAllCriterions(new ArrayList<Resource>(
                getResources()), criteria, limitingResource);
    }

    private List<Resource> selectSatisfiyingAllCriterions(
            List<Resource> resources,
            Collection<? extends Criterion> criterions,
            Boolean limitingResource) {

        List<Resource> result = new ArrayList<Resource>();
        for (Resource each : resources) {
            if (limitingResource.equals(each.isLimitingResource())
                    && each.satisfiesCriterions(criterions)) {
                result.add(each);
            }
        }
        return result;
    }

    @Override
    public List<Resource> findResourcesRelatedTo(List<Task> taskElements) {
        if (taskElements.isEmpty()) {
            return new ArrayList<Resource>();
        }
        Set<Resource> result = new LinkedHashSet<Resource>();
        result.addAll(findRelatedToSpecific(taskElements));
        result.addAll(findRelatedToGeneric(taskElements));
        return new ArrayList<Resource>(result);
    }

    @SuppressWarnings("unchecked")
    private List<Resource> findRelatedToGeneric(List<Task> taskElements) {
        String query = "SELECT DISTINCT resource FROM GenericResourceAllocation generic"
                + " JOIN generic.genericDayAssignmentsContainers container "
                + " JOIN container.dayAssignments dayAssignment"
                + " JOIN dayAssignment.resource resource"
                + " WHERE generic.task IN(:taskElements)";
        return getSession().createQuery(query)
                .setParameterList("taskElements",
                taskElements).list();
    }

    @SuppressWarnings("unchecked")
    private List<Resource> findRelatedToSpecific(List<Task> taskElements) {
        List<Resource> list = getSession()
                .createQuery(
                        "SELECT DISTINCT specificAllocation.resource "
                                + "FROM SpecificResourceAllocation specificAllocation "
                                + "WHERE specificAllocation.task IN(:taskElements) "
                                + "and specificAllocation.specificDaysAssignment IS NOT EMPTY")
                .setParameterList(
                "taskElements",
                taskElements).list();
        return list;
    }

    public List<Resource> getResources() {
        return list(Resource.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Resource> getAllLimitingResources() {
        return getSession().createCriteria(Resource.class).add(
                Restrictions.eq("limitingResource", true)).list();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Resource> getAllNonLimitingResources() {
        return getSession().createCriteria(Resource.class).add(
                Restrictions.eq("limitingResource", false)).list();
    }

    @Override
    public List<Machine> getMachines() {
        return list(Machine.class);
    }

    @Override
    public List<Resource> getRealResources() {
        List<Resource> list = new ArrayList<Resource>();
        list.addAll(getRealWorkers());
        list.addAll(getMachines());
        return list;
    }

    @Override
    public void save(Resource resource) {
        if (resource instanceof Worker || resource instanceof Machine) {
            if (resource.isLimitingResource() && resource.getLimitingResourceQueue() == null) {
                resource.setLimitingResourceQueue(LimitingResourceQueue.create());
            }
        }
        super.save(resource);
    }

}