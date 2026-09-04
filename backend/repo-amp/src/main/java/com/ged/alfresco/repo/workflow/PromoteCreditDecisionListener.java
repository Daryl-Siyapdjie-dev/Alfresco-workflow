package com.ged.alfresco.repo.workflow;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;

/**
 * A la completion de la tache "Validation et decision responsable", promeut la valeur
 * de la decision (gedwf:creditApprouve) au niveau de l'execution du process, pour qu'elle
 * soit visible par la passerelle de decision qui suit immediatement. Necessaire car les
 * proprietes de tache soumises via le formulaire generique de Share restent parfois
 * locales a la tache et ne sont pas automatiquement visibles par le process.
 */
public class PromoteCreditDecisionListener implements TaskListener {

    private static final String VARIABLE_NAME = "gedwf_creditApprouve";

    @Override
    public void notify(DelegateTask delegateTask) {
        Object value = delegateTask.getVariableLocal(VARIABLE_NAME);
        if (value == null) {
            value = delegateTask.getVariable(VARIABLE_NAME);
        }
        if (value != null) {
            delegateTask.getExecution().setVariable(VARIABLE_NAME, value);
        }
    }
}
