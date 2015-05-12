/*
 * (C) Copyright 2014 Académie de Rennes (http://www.ac-rennes.fr/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 *
 * Contributors:
 *  sjahier
 *  
 */
package fr.toutatice.ecm.platform.operation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.runtime.api.Framework;

import fr.toutatice.ecm.platform.automation.SetDocumentACL;
import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;
import fr.toutatice.ecm.platform.core.helper.ToutaticeFilter;
import fr.toutatice.ecm.platform.core.helper.ToutaticeOperationHelper;
import fr.toutatice.ecm.platform.core.helper.ToutaticeSilentProcessRunnerHelper;


@Operation(id = SetACLsDependingTargetAudience.ID, category = Constants.CAT_DOCUMENT, label = "Change the ACLs in the target audience ", description = "change the ACLs in the target audience. Si pas de public cible les ACLS sont hérités. ")
public class SetACLsDependingTargetAudience {

	public static final String ID = "Document.SetACLsDependingTargetAudience";
	private static final String ACE_FORMAT = "%s:%s:%s";
	
	private static final Log log = LogFactory.getLog(SetACLsDependingTargetAudience.class);

	@Param(name = "targetsXpath", required = false)
    protected String xpath;
	
	@Param(name = "isDirect", required = true)
	protected boolean isDirect;
	
    @Context
    protected CoreSession session;
    
    
    @OperationMethod
    public DocumentModel run(DocumentModel doc) throws Exception {
    	
    	InnerSilentSetACLsDependingTargetAudience runner = new InnerSilentSetACLsDependingTargetAudience(session, doc);
        runner.silentRun(true);
         	
    	return doc;
    }
    
    
    private class InnerSilentSetACLsDependingTargetAudience extends ToutaticeSilentProcessRunnerHelper {
    	private DocumentModel doc;
    	
    	public InnerSilentSetACLsDependingTargetAudience(CoreSession session, DocumentModel doc) {
            super(session);
            this.doc = doc;
        }

		@Override
		public void run() throws ClientException {
			
			DocumentRef docRef = this.doc.getRef();

	    	if(StringUtils.isBlank(xpath)){
	    		xpath="cld:publicCible";
	    	}
	    	String[] tabCibles = (String[]) this.doc.getPropertyValue(xpath);
	    	if(tabCibles==null || tabCibles.length==0){
	    		// suppression des ACLs Local => nettoyage d'une éventuelle rupture d'héritage.
	    		// si il n'y pas public cible il faut appliquer les ACLs du parent d'où le nettoyage de la rupture d'héritage
	        	ACP acp = this.doc.getACP();
	        	acp.removeACL(ACL.LOCAL_ACL);
	        	session.setACP(docRef, acp, true);
	    		
	    	}else{
	    		// récuperation de ACE en excluant les ACEs de permission READ
				ToutaticeFilter<ACE> filter = new ToutaticeFilter<ACE>() {

					@Override
					public boolean accept(ACE t) {
						return t.isGranted() && !SecurityConstants.READ.equals(t.getPermission());
					}
				};	
				ACL acl = null;
				if(isDirect){
					acl = ToutaticeDocumentHelper.getDocumentACL(session,this.doc,null,filter);
				}else{
					
					DocumentModel docParent = session.getParentDocument(this.doc.getRef());
					acl =ToutaticeDocumentHelper.getDocumentACL(session, docParent, null, filter);
				}
	    		
	    		// liste des ACEs à setter sur le document
	    		final List<String> entries = new ArrayList<String>(tabCibles.length);    		
	    		for (ACE ace : acl) {
					entries.add(String.format(ACE_FORMAT,ace.getUsername(),ace.getPermission(),true));
				}    		
	    		// ajout le public Cible    		    		
				for (String cible : tabCibles){
					entries.add(String.format(ACE_FORMAT, cible, SecurityConstants.READ, true));
				}	
				
			
				// Récupération du contexte propre à l'appel d'autres opérations Nuxeo
				AutomationService automation = null;
				try {
					automation = Framework.getService(AutomationService.class);
				} catch (Exception e) {
					log.warn("Error getting automation service, error: " + e.getMessage());
					throw new ClientException(e);
				}
				OperationContext ctx = new OperationContext(this.session);
				/* Appel à l'opération Document.SetACL */
				Map<String, Object> parameters = new HashMap<String, Object>();
				parameters.put("entries", StringUtils.join(entries, ','));
				parameters.put("overwrite", true);
				parameters.put("break", true);
				parameters.put("acl", ACL.LOCAL_ACL);
				ctx.setInput(doc);
				try {
					this.doc=(DocumentModel)ToutaticeOperationHelper.callOperation(ctx, SetDocumentACL.ID, parameters);					
				} catch (Exception e) {
					throw new ClientException(e);
				}
				
	    	}
			
		}
    	
    }
}
