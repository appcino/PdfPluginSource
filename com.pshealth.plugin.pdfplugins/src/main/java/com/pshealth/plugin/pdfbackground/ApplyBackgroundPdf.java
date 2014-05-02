
package com.pshealth.plugin.pdfbackground;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.poi.util.IOUtils;

import com.appiancorp.suiteapi.common.Name;
import com.appiancorp.suiteapi.common.exceptions.DuplicateUuidException;
import com.appiancorp.suiteapi.common.exceptions.PrivilegeException;
import com.appiancorp.suiteapi.common.exceptions.StorageLimitException;
import com.appiancorp.suiteapi.content.ContentConstants;
import com.appiancorp.suiteapi.content.ContentService;
import com.appiancorp.suiteapi.content.exceptions.HasChildrenException;
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
import com.appiancorp.suiteapi.process.framework.SmartServiceContext;

/**
 * @author Chitra
 *
 * Apr 25, 2014
 */
import com.appiancorp.suiteapi.process.palette.PaletteInfo; 
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;

@PaletteInfo(paletteCategory = "Custom Services", palette = "Document Generation") 
public class ApplyBackgroundPdf extends AppianSmartService {

	private static final Logger LOG = Logger
			.getLogger(ApplyBackgroundPdf.class);
	private final ContentService cs;
	private Long inputPdf;
	private Long firstPageBackground;
	private Long pdfAllPagesBackground;
	private Boolean deleteInputPdf;
	private String outputPdfName;
	private Long saveinFolder;
	private Long outputDocument;
	private String error;

	/* (non-Javadoc)
	 * @see com.appiancorp.suiteapi.process.framework.AppianSmartService#run()
	 */
	@Override
	public void run() throws SmartServiceException {
		// TODO Auto-generated method stub
		try {
			addBackgroundImageToPDF();
			// Delete the input file if deleteInputPdf is true
						if(deleteInputPdf){
							LOG.error("Deleting Input Pdf from path: "+ cs.getInternalFilename(inputPdf));
							Long[] versionIds = cs.getAllVersionIds(inputPdf);
							   for (int len = 0; len < versionIds.length; len++){
							    String docName = cs.getInternalFilename(versionIds[len]);  
							    File file = new File(docName); 
							    file.delete();
							    }
							   cs.delete(inputPdf, false);		
							   LOG.error("Deleted Input Pdf from path: "+ cs.getInternalFilename(inputPdf));
							   }				
							
		} catch (IOException e) {
			// TODO Auto-generated catch block
			error = e.getMessage();
			LOG.error(error);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			error = e.getMessage();
			LOG.error(error);
		} 
	}
	
	public void addBackgroundImageToPDF() throws IOException, InvalidContentException, HasChildrenException, PrivilegeException{
		ByteArrayOutputStream bos = null;
		
		
		if(!"".equals(inputPdf) && inputPdf != null){
			FileInputStream fis;
			fis = new FileInputStream(cs.getInternalFilename(inputPdf));
			bos = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			for (int readNum; (readNum = fis.read(buf)) != -1;){
				bos.write(buf, 0, readNum); //no doubt here is 0
			}
			LOG.error("Created Temporary file tempPdfFile.pdf");
			File tempPdfFile = File.createTempFile("tempPdfFile", ".pdf");
			PdfReader sourcePDFReader = null;
			try{
				sourcePDFReader = new PdfReader(bos.toByteArray());
				int noOfPages = sourcePDFReader.getNumberOfPages();
				PdfStamper stamp = new PdfStamper(sourcePDFReader, new FileOutputStream(tempPdfFile));
				int i = 0;
				Image templateImage = Image.getInstance(cs.getInternalFilename(pdfAllPagesBackground));
				templateImage.setAbsolutePosition(0, 0);
				PdfContentByte tempalteBytes;
				while (i < noOfPages){
					i++;
					if(i==1){
						Image templateImageOne;
						if(!"".equals(firstPageBackground) && firstPageBackground != null){
							templateImageOne = Image.getInstance(cs.getInternalFilename(firstPageBackground));
						}
						else{
							LOG.error("First Page Background not provided, Apply default page background");
							templateImageOne = Image.getInstance(cs.getInternalFilename(pdfAllPagesBackground));
						}
						templateImageOne.setAbsolutePosition(0, 0);
						tempalteBytes = stamp.getUnderContent(i);    
						tempalteBytes.addImage(templateImageOne);  	        		 
					}
					else{
						tempalteBytes = stamp.getUnderContent(i);    
						tempalteBytes.addImage(templateImage);
					}
				}
				stamp.close();
	         
				
				
				IOUtils.closeQuietly(bos);
				IOUtils.closeQuietly(fis);
	            //Create the document of the temp pdf
				createNewDocument(tempPdfFile);
			}
			catch (Exception ex){
				//  LOGGER.log(Level.INFO, "Error when applying template image as watermark");
				error = ex.getMessage();
				LOG.error(error);
			}
			finally{
				
				if(tempPdfFile != null){
					tempPdfFile.delete();
					LOG.error("Deleted Temp file from the location-"+ tempPdfFile.getAbsolutePath());
				}
			}
			
		}
	}
	
	/**
	 * @param tempPdfFile
	 */
	private void createNewDocument(File source) throws DuplicateUuidException,InsufficientNameUniquenessException, 
	PrivilegeException,StorageLimitException, InvalidContentException, IOException {
		Document doc = new Document();
		doc.setName(outputPdfName);
		doc.setExtension("pdf");
		doc.setParent(saveinFolder);
		doc.setSize((int) source.length());
		LOG.error("Created new document");
		outputDocument = cs.create(doc, ContentConstants.UNIQUE_NONE);
		writeDocument(source);
		LOG.error("Write document completed");
	}
		
	/**
	 * @param source
	 */
	private void writeDocument(File source) throws FileNotFoundException, InvalidContentException, IOException{
		FileInputStream fis = null;
		FileOutputStream fos = null;		
		try{
			fis = new FileInputStream(source);
			fos = new FileOutputStream(cs.getInternalFilename(outputDocument));
			IOUtils.copy(fis, fos);
		}
		finally{
			IOUtils.closeQuietly(fis);
			IOUtils.closeQuietly(fos);
		}
	}

	public ApplyBackgroundPdf(ContentService cs) {
		super();
		this.cs = cs;
	}

	public void onSave(MessageContainer messages) {
	}

	public void validate(MessageContainer messages) {
	}

	@Input(required = Required.ALWAYS)
	@Name("inputPdf")
	@DocumentDataType
	public void setInputPdf(Long val) {
		this.inputPdf = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("firstPageBackground")
	@DocumentDataType
	public void setFirstPageBackground(Long val) {
		this.firstPageBackground = val;
	}

	@Input(required = Required.ALWAYS)
	@Name("pdfAllPagesBackground")
	@DocumentDataType
	public void setPdfAllPagesBackground(Long val) {
		this.pdfAllPagesBackground = val;
	}

	@Input(required = Required.ALWAYS)
	@Name("deleteInputPdf")
	public void setDeleteInputPdf(Boolean val) {
		this.deleteInputPdf = val;
	}

	@Input(required = Required.ALWAYS)
	@Name("outputPdfName")
	public void setOutputPdfName(String val) {
		this.outputPdfName = val;
	}

	@Input(required = Required.ALWAYS)
	@Name("saveinFolder")
	@FolderDataType
	public void setSaveinFolder(Long val) {
		this.saveinFolder = val;
	}

	@Name("outputDocument")
	@DocumentDataType
	public Long getOutputDocument() {
		return outputDocument;
	}

	@Name("error")
	public String getError() {
		return error;
	}
}
