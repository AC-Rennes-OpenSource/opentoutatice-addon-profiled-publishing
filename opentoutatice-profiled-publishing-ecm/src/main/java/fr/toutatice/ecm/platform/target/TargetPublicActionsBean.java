package fr.toutatice.ecm.platform.target;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.faces.FacesMessages;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.Filter;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.ecm.platform.ui.web.util.ComponentUtils;
import org.nuxeo.ecm.webapp.base.InputController;

import fr.toutatice.ecm.platform.constants.PPFConstants;
import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;

@Name("targetPublicActions")
@Scope(ScopeType.PAGE)
@Install(precedence = Install.DEPLOYMENT)
public class TargetPublicActionsBean extends InputController implements TargetPublicBean, Serializable,PPFConstants {

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(TargetPublicActionsBean.class);

	@In(create = true)
	protected transient NavigationContext navigationContext;

	@In(create = true, required = false)
	protected transient CoreSession documentManager;

	protected List<String> groupName;
	protected String label;
	protected Integer order;
	
	

	// -------------------------- gestion de la liste des publics cibles authorisés -----------------------------------

	@Override
	public void addTargetPublicAuth() throws ClientException {
		DocumentModel doc = navigationContext.getCurrentDocument();
		if (groupName == null || groupName.isEmpty()) {
			String message = ComponentUtils.translate(FacesContext.getCurrentInstance(), "ppf.error.group.null");
			FacesMessages.instance().add(message);
		} else {
			try {
				List<HashMap<String, Object>> lst = (List<HashMap<String, Object>>) doc.getPropertyValue(XPATH_LST_PUBLIC_AUTH);
				boolean ismissing = true;
				if (lst != null && !lst.isEmpty()) {
					// vérification de la non existance de l'id dans la liste
					for (HashMap<String, Object> data : lst) {
						String id = (String) data.get("pcid");
						if (groupName.get(0).equals(id)) {
							ismissing = false;
							break;
						}
					}
				}
				if (ismissing) {
					HashMap<String, Object> data = new HashMap<String, Object>();
					data.put("pcid", this.groupName.get(0));
					if (this.label == null) {
						this.label = this.groupName.get(0);
					}
					data.put("pclabel", this.label);
					if(order==null){
						data.put("pcorder", 999);
					}else{
						data.put("pcorder", order);
					}
					lst.add(data);
					doc.setPropertyValue(XPATH_LST_PUBLIC_AUTH, lst.toArray(new Object[lst.size()]));
					documentManager.saveDocument(doc);

				} else {
					String message = ComponentUtils.translate(FacesContext.getCurrentInstance(), "ppf.error.group.already.exist");
					FacesMessages.instance().add(message);
				}
			} catch (PropertyException e) {
				log.error(" *** " + e.getMessage());
			}

			// remise à blanc
			this.groupName.clear();
			this.label = null;
			this.order = null;
		}
	}

	@Override
	public void removeTargetPublicAuth(String groupid) throws ClientException {
		DocumentModel doc = navigationContext.getCurrentDocument();
		List<HashMap<String, Object>> lst = (List<HashMap<String, Object>>) doc.getPropertyValue(XPATH_LST_PUBLIC_AUTH);

		if (lst != null && !lst.isEmpty()) {
			Object dataToRemove = null;
			// vérification de la non existance de l'id dans la liste
			for (HashMap<String, Object> data : lst) {
				String id = (String) data.get("pcid");
				if (groupid.equals(id)) {
					dataToRemove = data;
					break;
				}
			}
			if (dataToRemove != null) {
				lst.remove(dataToRemove);
				doc.setPropertyValue(XPATH_LST_PUBLIC_AUTH, lst.toArray(new Object[lst.size()]));
				documentManager.saveDocument(doc);
			} else {
				new ClientException("Le groupe à supprimer n'a pas été trouvé dans la liste ");
			}
		}

	}

	@Override
	public List<HashMap<String, Object>> getLstPublishAuth() throws ClientException{
		DocumentModel psDoc = navigationContext.getCurrentDocument();

		// il faut récupérer la liste en mode unrestricted car le document à lui même été récupére avec ce mode.
		// (l'utilisateur connecté n'a pas forcément le droit de lecture sur les parents du document courant)
		List<HashMap<String, Object>> lst=null;
		List<String> lstXpaths = new ArrayList<String>(1);
		lstXpaths.add(XPATH_LST_PUBLIC_AUTH);
		Filter filter = new Filter() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean accept(DocumentModel docFilter) {
				boolean status = false;

				try {
					status = ToutaticeDocumentHelper.isAPublicationSpaceDocument(docFilter);
				} catch (Exception e) {
					log.error("Failed to filter the publish space documents, error: " + e.getMessage());
					status = false;
				}

				return status;
			}
		};
		
		Map<String,Property> mapPpty = ToutaticeDocumentHelper.getPropertiesParentDoc(documentManager, psDoc, lstXpaths, filter, true, true);
		if(mapPpty!=null){
			lst = (List<HashMap<String, Object>>) mapPpty.get(XPATH_LST_PUBLIC_AUTH).getValue();
			Collections.sort(lst, new PublicTargetComparator());
		}
		return lst;
	}

	@Override
	public List<String> getGroupName() {
		return groupName;
	}

	@Override
	public void setGroupName(List<String> selectedEntry) {
		this.groupName = selectedEntry;
	}

	@Override
	public String getLabel() {
		return label;
	}

	@Override
	public void setLabel(String label) {
		this.label = label;
	}

	@Override
	public Integer getOrder() {
		return order;
	}

	@Override
	public void setOrder(Integer order) {
		this.order = order;
	}

	// --------------------------- Gestion des publics cibles sélectionnés -----------------------------------------------

	private Map<String,String> lstItemsSelectable = null;	
	private Map<String,String> lstItemsChosen = null;	
	private Map<String,String> targetsDocument = null; 
	private Boolean isDefaultValue = null;
	private List<String> lstValuesChosen = new ArrayList<String>();
	
	/**
	 * 
	 * @return map dont la clef est l'id du publicCible et la valeur le label du publicCible
	 *        map (cle:pcid, value:pclabel)
	 * @throws ClientException
	 */
	public Map<String,String> getTargetsDocument() throws ClientException{
	
		if(targetsDocument==null || targetsDocument.isEmpty()){
			
			List<HashMap<String, Object>> lstMap = getLstPublishAuth();
			if (lstMap != null) {
				targetsDocument = new LinkedHashMap<String, String>(lstMap.size());
				for (HashMap<String, Object> map : lstMap) {
					targetsDocument.put((String)map.get("pcid"), (String)map.get("pclabel"));
				}
			}
		}
		return targetsDocument;
	}
	
	/** 
	 * Map contenant les cibles selectionnable pour le document
	 * @return map (cle:pclabel, value:pcid)
	 * @throws ClientException
	 */
	public Map<String,String> getLstItemsSelectable() throws ClientException {
		
		if(lstItemsSelectable==null && getTargetsDocument()!=null){
			initListsItems();
		}
		return lstItemsSelectable;
	}
	
	/**
	 * Map contenant les cibles selectionnés pour le document
	 * @return map (cle:pclabel, value:pcid)
	 * @throws ClientException
	 */
	public Map<String, String> getLstItemsChosen() throws ClientException{
		if(lstItemsChosen==null && getTargetsDocument()!=null){
			initListsItems();
		}
		return lstItemsChosen;
	}
	
	
	
	public List<String> getKeysChosen() throws ClientException{
		if(lstItemsChosen==null){
			getLstItemsChosen();
		}
		return new ArrayList<String>(lstItemsChosen.keySet());	
	}
	
	public List<String> getLstValuesChosen() {
		return lstValuesChosen;
	}

	public void setLstValuesChosen(List<String> lstValuesChosen) throws ClientException{
		this.lstValuesChosen = lstValuesChosen;
		updatePropertyDocument();
	}
	


	private void initListsItems(){
		
			// vérifier si le document courant posséde des cibles
			DocumentModel doc = navigationContext.getCurrentDocument();
			lstItemsChosen = new LinkedHashMap<String,String>();
			lstItemsSelectable = new LinkedHashMap<String,String>();
			
			Property ppt = doc.getProperty(XPATH_LST_TARGETED_PUBLIC);
			List<String>tabCibles = ppt.convertTo(ppt.getValue(),List.class);
			for (String item : getTargetsDocument().keySet()) {
				if(tabCibles.contains(item)){
					lstItemsChosen.put(targetsDocument.get(item), item);
					lstValuesChosen.add(item);
				}else{
					lstItemsSelectable.put(targetsDocument.get(item), item);	
				}
				
			}
			
	}
	
	private void updatePropertyDocument() throws PropertyException, ClientException{
		
		DocumentModel doc = navigationContext.getChangeableDocument();
		if(doc == null){
			doc = navigationContext.getCurrentDocument();
		}
		String[] tab = lstValuesChosen.toArray(new String[lstValuesChosen.size()]);		
		doc.setProperty("toutatice", "lstTargetedPublic", tab);		
	}

	// ------------------------------------ classes internes -------------------------------------------

	protected class PublicTargetComparator implements Comparator<HashMap<String, Object>> {

		List<HashMap<String, Object>> list;

		@Override
		public int compare(HashMap<String, Object> arg0, HashMap<String, Object> arg1) {
			int res = 0;
			Long order0 = (Long) arg0.get("pcorder");
			Long order1 = (Long) arg1.get("pcorder");
			res = order0.compareTo(order1);
			if (res == 0) {
				String label0 = (String) arg0.get("pclabel");
				String label1 = (String) arg1.get("pclabel");
				res = label0.compareTo(label1);
			}
			return res;
		}

	}
	
	 protected class Target{
		 String pcid;
		 String pclabel;
		 
		 public Target(String id, String label){
			 this.pcid = id;
			 this.pclabel = label;
		 }
		 
		public String getPcid() {
			return pcid;
		}
		public void setPcid(String pcid) {
			this.pcid = pcid;
		}
		public String getPclabel() {
			return pclabel;
		}
		public void setPclabel(String pclabel) {
			this.pclabel = pclabel;
		}
	 }


}
