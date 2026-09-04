package com.ged.alfresco.repo.workflow;

import java.util.Date;
import java.util.List;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.ContextLoader;

/**
 * Etape "Archivage" du workflow Gestion de Credit : automatique, sans utilisateur.
 * Applique l'aspect gedwf:archived (avec la date du jour) a tous les documents
 * du dossier (bpm_package) une fois le credit signe.
 *
 * Instancie directement par Activiti via activiti:class (pas delegateExpression) : les beans
 * Spring de ce module ne sont pas visibles depuis les expressions BPMN dans cette configuration
 * Alfresco/Activiti. Le NodeService est recupere via ContextLoader.getCurrentWebApplicationContext(),
 * qui reutilise le contexte Spring DEJA demarre du webapp (ApplicationContextHelper, utilise dans une
 * premiere version, essaie au contraire de re-bootstrapper un second contexte Spring complet depuis
 * zero en pleine requete : ca provoquait une erreur silencieuse a chaque completion de tache).
 */
public class ArchivageDelegate implements JavaDelegate {

    private static final String GED_WORKFLOW_NAMESPACE = "http://www.ged.com/model/workflow/1.0";
    public static final QName ASPECT_ARCHIVED = QName.createQName(GED_WORKFLOW_NAMESPACE, "archived");
    public static final QName PROP_ARCHIVE_DATE = QName.createQName(GED_WORKFLOW_NAMESPACE, "archiveDate");

    @Override
    public void execute(DelegateExecution execution) {
        NodeRef packageNode = extractNodeRef(execution.getVariable("bpm_package"));
        if (packageNode == null) {
            return;
        }
        NodeService nodeService = getNodeService();
        // Action systeme automatique : ne doit pas dependre des droits de l'utilisateur qui a
        // complete la tache precedente (ex: signataire1 n'a pas forcement le droit d'ecriture).
        AuthenticationUtil.runAsSystem(() -> {
            List<ChildAssociationRef> documents = nodeService.getChildAssocs(packageNode);
            for (ChildAssociationRef assoc : documents) {
                NodeRef document = assoc.getChildRef();
                if (!nodeService.hasAspect(document, ASPECT_ARCHIVED)) {
                    nodeService.addAspect(document, ASPECT_ARCHIVED, null);
                }
                nodeService.setProperty(document, PROP_ARCHIVE_DATE, new Date());
            }
            return null;
        });
    }

    /**
     * bpm_package est expose par Alfresco sous forme d'un ActivitiScriptNode (pas un NodeRef brut) :
     * c'est le cas qui a fait echouer silencieusement une premiere version de cette classe (aucune
     * exception levee, l'aspect n'etait simplement jamais applique).
     */
    private static NodeRef extractNodeRef(Object variable) {
        if (variable instanceof NodeRef) {
            return (NodeRef) variable;
        }
        if (variable instanceof ActivitiScriptNode) {
            return ((ActivitiScriptNode) variable).getNodeRef();
        }
        return null;
    }

    private static NodeService getNodeService() {
        ApplicationContext context = ContextLoader.getCurrentWebApplicationContext();
        ServiceRegistry serviceRegistry = (ServiceRegistry) context.getBean("ServiceRegistry");
        return serviceRegistry.getNodeService();
    }
}
