package com.ged.alfresco.repo.behavior;

import java.util.Date;

import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.InitializingBean;

/**
 * Exemple de behavior Java : a la creation d'un noeud ged:contrat,
 * renseigne automatiquement la date de signature si elle n'a pas ete saisie.
 */
public class ContratBehaviour implements NodeServicePolicies.OnCreateNodePolicy, InitializingBean {

    private static final String GED_NAMESPACE = "http://www.ged.com/model/content/1.0";
    public static final QName TYPE_CONTRAT = QName.createQName(GED_NAMESPACE, "contrat");
    public static final QName PROP_DATE_SIGNATURE = QName.createQName(GED_NAMESPACE, "dateSignature");

    private PolicyComponent policyComponent;
    private NodeService nodeService;

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Override
    public void afterPropertiesSet() {
        policyComponent.bindClassBehaviour(
                NodeServicePolicies.OnCreateNodePolicy.QNAME,
                TYPE_CONTRAT,
                new JavaBehaviour(this, "onCreateNode"));
    }

    @Override
    public void onCreateNode(ChildAssociationRef childAssocRef) {
        var nodeRef = childAssocRef.getChildRef();
        if (nodeService.getProperty(nodeRef, PROP_DATE_SIGNATURE) == null) {
            nodeService.setProperty(nodeRef, PROP_DATE_SIGNATURE, new Date());
        }
    }
}
