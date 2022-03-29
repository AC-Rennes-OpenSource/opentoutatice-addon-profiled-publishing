package fr.toutatice.ecm.platform.listener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.schema.FacetNames;

import fr.toutatice.ecm.platform.constants.PPFConstants;
import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;
import fr.toutatice.ecm.platform.core.helper.ToutaticeOperationHelper;
import fr.toutatice.ecm.platform.core.utils.exception.ToutaticeException;

public class PPFEventListener implements EventListener, PPFConstants {

	private static final Log log = LogFactory.getLog(PPFEventListener.class);
	private static final String PARENT_PATH = "parentPath";

	@Override
	public void handleEvent(Event event) throws ClientException {
		if (event.getContext() instanceof DocumentEventContext) {
			DocumentEventContext eventContext = (DocumentEventContext) event.getContext();
			DocumentModel doc = eventContext.getSourceDocument();

			if (null != doc && (doc.isImmutable() || doc.hasFacet(FacetNames.HIDDEN_IN_NAVIGATION) || doc.hasFacet(FacetNames.SYSTEM_DOCUMENT))) {
				return;
			}

			// héritage des targets
			if (DocumentEventTypes.EMPTY_DOCUMENTMODEL_CREATED.equals(event.getName())) {

				// récupérer le document parent
				String parentPath = (String) eventContext.getProperty(PARENT_PATH);
				if (null == parentPath) {
					// Ignore fake documents (i.e. used by blog site & web site to validate themselves)
					return;
				}
				
				CoreSession session = eventContext.getCoreSession();
				DocumentModel parent = session.getDocument(new PathRef(parentPath));
				Boolean isPublishSpace = isBelongPublishSpace(parent, eventContext.getCoreSession());
				if (isPublishSpace) {
					String[] ppteValue = (String[]) parent.getPropertyValue(XPATH_LST_TARGETED_PUBLIC);
					if (ppteValue != null && ppteValue.length > 0) {
						doc.setPropertyValue(XPATH_LST_TARGETED_PUBLIC, ppteValue);
					}
				}

			}

			// mise à jour des ACLs avant creation et avant modification du document
			if (DocumentEventTypes.BEFORE_DOC_UPDATE.equals(event.getName())) {
				Boolean isPublishSpace = isBelongPublishSpace(doc, eventContext.getCoreSession());
				if(isPublishSpace){
					updateAcls(doc, eventContext.getCoreSession(), false);
				}
			}
			if (DocumentEventTypes.DOCUMENT_CREATED.equals(event.getName())) {
				Boolean isPublishSpace = isBelongPublishSpace(doc, eventContext.getCoreSession());
				if(isPublishSpace){
					updateAcls(doc, eventContext.getCoreSession(), true);
				}
			}
		}

	}

	private Boolean isBelongPublishSpace(DocumentModel doc, CoreSession cSession) {
		boolean b = false;

		DocumentModelList spaceDocsList = ToutaticeDocumentHelper.getParentPublishSpaceList(cSession, doc, true, false);
		b = (spaceDocsList != null && !spaceDocsList.isEmpty());
		return b;
	}

	private void updateAcls(DocumentModel doc, CoreSession cSession, boolean immediate) throws PropertyException, ClientException {
		Property pptPublicCible = null;

		try {
			pptPublicCible = doc.getProperty(PPFConstants.XPATH_LST_TARGETED_PUBLIC);
		} catch (Exception e1) {
			log.warn("Property not found in '" + doc.getName() + "', error : " + e1.getMessage());
			immediate = false;
		}
		boolean isPcDirty = false;
		// si le public cible existe et a changé alors mise à jours des acls
		if (pptPublicCible.getValue() != null) {
			isPcDirty = pptPublicCible.isDirty();
		}

		if (isPcDirty || immediate) {

			try {
				ToutaticeOperationHelper.runOperationChain(cSession, "updateAcls", doc);
			} catch (ToutaticeException e) {
				log.error("Failed to update ACLs: '" + doc.getName() + "', error: " + e.getMessage());
			}
		}

	}
}
