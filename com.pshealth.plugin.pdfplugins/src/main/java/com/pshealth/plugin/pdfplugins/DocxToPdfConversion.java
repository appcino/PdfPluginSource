package com.pshealth.plugin.pdfplugins;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;



import org.apache.log4j.Logger;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xwpf.converter.pdf.PdfConverter;
import org.apache.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import com.appiancorp.suiteapi.common.Name;
import com.appiancorp.suiteapi.common.exceptions.DuplicateUuidException;
import com.appiancorp.suiteapi.common.exceptions.PrivilegeException;
import com.appiancorp.suiteapi.common.exceptions.StorageLimitException;
import com.appiancorp.suiteapi.content.ContentConstants;
import com.appiancorp.suiteapi.content.ContentService;
import com.appiancorp.suiteapi.content.exceptions.InsufficientNameUniquenessException;
import com.appiancorp.suiteapi.content.exceptions.InvalidContentException;
import com.appiancorp.suiteapi.knowledge.Document;
import com.appiancorp.suiteapi.knowledge.DocumentDataType;
import com.appiancorp.suiteapi.knowledge.FolderDataType;
import com.appiancorp.suiteapi.process.exceptions.SmartServiceException;
import com.appiancorp.suiteapi.process.framework.AppianSmartService;
import com.appiancorp.suiteapi.process.framework.Input;
import com.appiancorp.suiteapi.process.framework.MessageContainer;
import com.appiancorp.suiteapi.process.framework.Required;



import com.appiancorp.suiteapi.process.palette.PaletteInfo; 

@PaletteInfo(paletteCategory = "Custom Services", palette = "Document Generation") 
public class DocxToPdfConversion extends AppianSmartService {

	private static final Logger LOG = Logger
			.getLogger(DocxToPdfConversion.class);
	private final ContentService cs;
	private Long inputDocument;
	private Long newDocumentFolder;
	private String newDocumentName;
	private String newDocumentDescription;
	private Long output;
	private String error;

	@Override
	public void run() throws SmartServiceException {
		// TODO Auto-generated method stub
		//create a temporary document
		File tempFile = null;
		long startTime = System.currentTimeMillis();
		
		try{
			// create temporary file
			tempFile = File.createTempFile("tempFile", ".pdf");
			LOG.error("Temp file path "+tempFile.getAbsolutePath());
			// convert docx to pdf
			doConversion(tempFile);
			// create new document and copy temporary file
			createNewDocument(tempFile);
		}
		catch(Exception e){
			error = e.getMessage();
			throw new SmartServiceException.Builder(DocxToPdfConversion.class, e).build();
		}
		finally{
			// clean temporary file
			if(tempFile!=null){
				tempFile.delete();
			}
		}
		LOG.error("Conversion completed in " + ( System.currentTimeMillis() - startTime ) + " ms." );
	
	}
	
	private void doConversion(File tempFile) throws InvalidContentException, FileNotFoundException, IOException, 
			DuplicateUuidException, InsufficientNameUniquenessException, PrivilegeException, StorageLimitException{
		
		LOG.error("DOCX to PDF conversion started");
		
		String filePath="";
		
		filePath = cs.getInternalFilename(inputDocument);
		
		InputStream is = null;
		XWPFDocument xwpfDocument = null;
		try{
			is = new FileInputStream(filePath);
			xwpfDocument = new XWPFDocument( is );
			
		}
		finally{
			IOUtils.closeQuietly(is);
		}
		
		LOG.error("Loaded document: "+filePath);
		
		OutputStream out = null;
		PdfOptions options = null;
		
		LOG.error("Converting file.. ");
		
		try{
			out = new FileOutputStream( tempFile );
			PdfConverter.getInstance().convert( xwpfDocument, out, options );
		}
		finally{
			IOUtils.closeQuietly(out);
			LOG.error("Conversion completed ");
		}
		
	}
	
	
	private void createNewDocument(File source) throws DuplicateUuidException,InsufficientNameUniquenessException, 
	PrivilegeException,StorageLimitException, InvalidContentException, IOException {
		Document doc = new Document();
		
		if("".equals(newDocumentName) || newDocumentName == null){
			doc.setName(removeExtension(cs.getExternalFilename(inputDocument)));
		}
		else{
			doc.setName(newDocumentName);
		}
		
		if(!"".equals(newDocumentDescription)){
			doc.setDescription(newDocumentDescription);
		}
		
		doc.setExtension("pdf");
		doc.setParent(newDocumentFolder);
		doc.setSize((int) source.length());
		LOG.error("Created new document");
		output = cs.create(doc, ContentConstants.UNIQUE_NONE);
		writeDocument(source);
		LOG.error("Write document completed");
		
		}

	private void writeDocument(File source) throws FileNotFoundException, InvalidContentException, IOException{
		FileInputStream fis = null;
		FileOutputStream fos = null;
		
		try{
			fis = new FileInputStream(source);
			fos = new FileOutputStream(cs.getInternalFilename(output));
			IOUtils.copy(fis, fos);
		}
		finally{
			IOUtils.closeQuietly(fis);
			IOUtils.closeQuietly(fos);
		}
	}
	
	private String removeExtension(String fileName){
		if(!"".equals(fileName) && fileName != null && fileName.lastIndexOf(".")>1){
			return fileName.substring(0,fileName.lastIndexOf("."));
		}
		return fileName;
	}
	
	public DocxToPdfConversion(ContentService cs) {
		super();
		this.cs = cs;
	}

	public void onSave(MessageContainer messages) {
	}

	public void validate(MessageContainer messages) {
	}

	@Input(required = Required.ALWAYS)
	@Name("inputDocument")
	@DocumentDataType
	public void setInputDocument(Long val) {
		this.inputDocument = val;
	}

	@Input(required = Required.ALWAYS)
	@Name("newDocumentFolder")
	@FolderDataType
	public void setNewDocumentFolder(Long val) {
		this.newDocumentFolder = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("newDocumentName")
	public void setNewDocumentName(String val) {
		this.newDocumentName = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("newDocumentDescription")
	public void setNewDocumentDescription(String val) {
		this.newDocumentDescription = val;
	}

	@Name("output")
	@DocumentDataType
	public Long getOutput() {
		return output;
	}

	@Name("error")
	public String getError() {
		return error;
	}

}
