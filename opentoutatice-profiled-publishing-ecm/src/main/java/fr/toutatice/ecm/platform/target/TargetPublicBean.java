package fr.toutatice.ecm.platform.target;

import java.util.HashMap;
import java.util.List;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;

public interface TargetPublicBean {

	public abstract void addTargetPublicAuth() throws ClientException;
	
	public abstract void removeTargetPublicAuth(String id) throws ClientException;
	
	public void modifyTarget(String groupid);	

	public abstract List<String> getGroupName();

	public abstract void setGroupName(List<String> selectedEntry);

	public abstract String getLabel();

	public abstract void setLabel(String label);

	public abstract Integer getOrder();

	public abstract void setOrder(Integer order);
	
	public List<HashMap<String, Object>> getLstPublishAuth() throws ClientException;
	
	public void cancelModify();
	
	public void validateModify();

}