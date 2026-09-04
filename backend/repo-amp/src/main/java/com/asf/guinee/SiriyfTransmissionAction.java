package com.asf.guinee;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Map;

public class SiriyfTransmissionAction implements JavaDelegate {

    private static final Logger LOG = LoggerFactory.getLogger(SiriyfTransmissionAction.class);

    private NodeService nodeService;

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        LOG.info("=== START: Simulation de la transmission vers API SIRYF ===");
        
        LOG.info("Connexion à l'API SIRYF...");
        Thread.sleep(1000); // Simulation d'attente réseau
        LOG.info("Authentification réussie. Envoi du dossier réglementaire...");
        Thread.sleep(1000);
        LOG.info("Réponse de la Banque Centrale: 200 OK - Dossier accepté.");

        // Récupérer le conteneur du workflow
        Object bpmPackageObj = execution.getVariable("bpm_package");
        if (bpmPackageObj instanceof ActivitiScriptNode) {
            ActivitiScriptNode bpmPackage = (ActivitiScriptNode) bpmPackageObj;
            NodeRef packageNodeRef = bpmPackage.getNodeRef();

            // Mettre à jour le statut des documents dans le workflow
            QName typeDeclaration = QName.createQName("http://www.asf.guinee.com/model/siriyf/1.0", "declaration");
            QName propStatut = QName.createQName("http://www.asf.guinee.com/model/siriyf/1.0", "statut");

            for (org.alfresco.service.cmr.repository.ChildAssociationRef childRef : nodeService.getChildAssocs(packageNodeRef)) {
                NodeRef docNodeRef = childRef.getChildRef();
                QName nodeType = nodeService.getType(docNodeRef);
                
                if (typeDeclaration.equals(nodeType)) {
                    LOG.info("Archivage du document (mise à jour statut) : " + docNodeRef);
                    nodeService.setProperty(docNodeRef, propStatut, "Archivé");
                }
            }
        }
        
        LOG.info("=== END: Simulation SIRYF terminée ===");
    }
}
