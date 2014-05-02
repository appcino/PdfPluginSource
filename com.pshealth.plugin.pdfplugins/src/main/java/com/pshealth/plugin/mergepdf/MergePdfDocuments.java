package com.pshealth.plugin.mergepdf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.util.PDFMergerUtility;
import org.apache.poi.util.IOUtils;




import com.appiancorp.core.expr.fn.info.IsNumber;
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
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.pshealth.plugin.pdfresources.Data;


@PaletteInfo(paletteCategory = "Custom Services", palette = "Document Generation") 
public class MergePdfDocuments extends AppianSmartService {

	private static final Logger LOG = Logger.getLogger(MergePdfDocuments.class);
	private final ContentService cs;
	private Long[] inputPdfs;
	private Long mergePdfFolder;
	private String mergePdfName;
	private String mergePdfDescription;
	private Boolean mergePdfAddPageNumber;
	private Long mergePdfPageNoStyle;
	private Long mergePdfStartNumberingPageNo;
	private String mergePdfPageNoFontType;
	private Long mergePdfNumberingBottomMargin;
	private Long mergePdfNumberingRightMargin;
	private Double mergePdfFontSize;
	
	private Long output;
	private String error;

	@Override
	public void run() throws SmartServiceException {
		// TODO Auto-generated method stub
if(inputPdfs != null && inputPdfs.length > 1){
	try{
		doMergeAndPageNumbering();
	}
	catch(Exception e){
		error = e.getMessage();
		LOG.error(e.getMessage());
	}	
}
else{
	error = "Number of pdf should be more than one";
}
	
		
	}

	public void doMergeAndPageNumbering() throws SmartServiceException{
		File tempFile = null;
		long startTime = System.currentTimeMillis();
		
		try{
			// create temporary file
			tempFile = File.createTempFile("tempMergeFile", ".pdf");
			LOG.error("Temp file path "+tempFile.getAbsolutePath());
			mergePDF(inputPdfs,tempFile);
			
			if(mergePdfAddPageNumber){
				tempFile = addPageNumbers(tempFile);
			}
			// add page number logic
			createNewDocument(tempFile);
		}
		catch(Exception e){
			error = e.getMessage();
			e.printStackTrace();
			throw new SmartServiceException.Builder(MergePdfDocuments.class, e).build();
		}
		finally{
			if(tempFile != null){
				tempFile.delete();
			}
		}
		LOG.error("Merged PDF in " + ( System.currentTimeMillis() - startTime ) + " ms." );
	} 
	
	
	private void mergePDF(Long[] documents, File tempMergeFile) throws FileNotFoundException, InvalidContentException,
						COSVisitorException, IOException{
		FileOutputStream fos = null;
		
		PDFMergerUtility pdfMerge = new PDFMergerUtility();
		
		try{
			fos = new FileOutputStream(tempMergeFile);
			LOG.error("Merge PDF: Created temp file output stream");
			for(Long docId: documents){
				LOG.error("Merge PDF: documents id "+docId);
				LOG.error("Merge PDF: documents internal path "+cs.getInternalFilename(docId));
				pdfMerge.addSource(cs.getInternalFilename(docId));
			}
			
			pdfMerge.setDestinationStream(fos);
			pdfMerge.mergeDocuments();
		}
		finally{
			IOUtils.closeQuietly(fos);
		}
		
	}
	
	private File addPageNumbers(File tempPdf) throws IOException, DocumentException{
		
		String fontFileName = null;
		long mergePdfNumberingType;
		boolean isPageNumberTypePresent = false;
		
		if(mergePdfStartNumberingPageNo == null || mergePdfStartNumberingPageNo < 1){
			mergePdfStartNumberingPageNo = 1L;
		}
		LOG.error("mergePdfStartNumberingPageNo "+mergePdfStartNumberingPageNo);
				
		if(mergePdfPageNoFontType != null && Arrays.asList(MergePdfConstants.ALLOWED_FONT_NAMES).contains(mergePdfPageNoFontType.toLowerCase())){
			int index = Arrays.asList(MergePdfConstants.ALLOWED_FONT_NAMES).indexOf(mergePdfPageNoFontType.toLowerCase());
			fontFileName = MergePdfConstants.ALLOWED_FONT_FILES[index];
		}
		else{
			LOG.error("Applied default font type");
			mergePdfPageNoFontType = MergePdfConstants.DEFAULT_FONT_FILE;
			fontFileName = MergePdfConstants.DEFAULT_FONT_FILE;
		}
		LOG.error("fontFileName "+fontFileName);
		
		
		if(mergePdfPageNoStyle != null){
			LOG.error("mergePdfPageNoStyle "+mergePdfPageNoStyle);
			for(int l = 0; l < MergePdfConstants.ALLOWED_PAGE_NUMBER_TYPE.length; l++){
				if(Long.parseLong(MergePdfConstants.ALLOWED_PAGE_NUMBER_TYPE[l].toString()) == mergePdfPageNoStyle){
					mergePdfNumberingType = mergePdfPageNoStyle;
					isPageNumberTypePresent = true;
					break;
				}
								
			}
				
			
		}
		if(!isPageNumberTypePresent){
			LOG.error("Applying default page style");			
			mergePdfNumberingType = MergePdfConstants.DEFAULT_PAGE_NUMBER_TYPE;
			mergePdfPageNoStyle = Long.parseLong(MergePdfConstants.DEFAULT_PAGE_NUMBER_TYPE.toString());
		}
		
		
		
		
		
		if(mergePdfNumberingBottomMargin == null){
			mergePdfNumberingBottomMargin = Long.parseLong(MergePdfConstants.DEFAULT_BOTTOM_MARGIN.toString());
		}
		
		LOG.error("mergePdfNumberingBottomMargin "+mergePdfNumberingBottomMargin);
		
		if(mergePdfNumberingRightMargin == null){
			mergePdfNumberingRightMargin = 0L;
		}
		
		LOG.error("mergePdfNumberingRightMargin "+mergePdfNumberingRightMargin);
		
		InputStream is = Data.class.getResourceAsStream(fontFileName);
		
		if(mergePdfFontSize == null){
			mergePdfFontSize = MergePdfConstants.DEFAULT_FONT_SIZE;
		}
		LOG.error("Created input stream ");
		
		byte[] rBytes = IOUtils.toByteArray(is);
		BaseFont bf_times = BaseFont.createFont(fontFileName, BaseFont.WINANSI, true, false, rBytes, null);
		Font font = new Font(bf_times, Float.parseFloat(mergePdfFontSize.toString()));
		
		com.lowagie.text.Document document = new com.lowagie.text.Document(PageSize.A4, Float.parseFloat(MergePdfConstants.DEFAULT_LEFT_MARGIN.toString()) , Float.parseFloat(mergePdfNumberingRightMargin.toString()), Float.parseFloat(MergePdfConstants.DEFAULT_TOP_MARGIN.toString()), Float.parseFloat(mergePdfNumberingBottomMargin.toString()));
		LOG.error("Created document ");
		
		PdfWriter writer = null;
		File tempFile = null;
		try{
			tempFile = File.createTempFile("tempNumberingFile", ".pdf");
			
			LOG.error("Created temporary file "+tempFile.getAbsolutePath());
			writer = PdfWriter.getInstance(document, new FileOutputStream(tempFile));
			LOG.error("Created writer ");
			document.open();
			
			PdfReader reader = new PdfReader(tempPdf.getAbsolutePath());
			LOG.error("Created Pdf Reader ");
			int totalPages = reader.getNumberOfPages();
			LOG.error("totalPages "+totalPages);
			
			//int n=totalPages- Integer.parseInt(mergePdfStartNumberingPageNo.toString()) + 1;
			PdfImportedPage page = null;
			//int pageNum = 1;
			
			for (int i = 1; i <= totalPages; i++) {
				page = writer.getImportedPage(reader, i);
				page.setHeight(842);
				if(i >=  mergePdfStartNumberingPageNo){
					if(mergePdfPageNoStyle == 1){
						ColumnText.showTextAligned(writer.getDirectContent(),Element.ALIGN_RIGHT, new Phrase(i + "", font), (document.right()),mergePdfNumberingBottomMargin, 0f);
						//LOG.error("pageNum "+pageNum);
					}
					else{
						 ColumnText.showTextAligned(writer.getDirectContent(),Element.ALIGN_RIGHT, new Phrase("Page "+ i + " of " + totalPages, font),(document.right()), mergePdfNumberingBottomMargin, 0f);
						 //LOG.error("pageNum "+pageNum);
					}
					//pageNum++;
				}
				Image instance = Image.getInstance(page);
				document.add(instance);
			}
			document.close();
			 
		}
		finally{
			IOUtils.closeQuietly(is);
			
			if(tempPdf!=null){
				tempPdf.delete();
			}
			 LOG.error("tempFile "+tempFile.getAbsolutePath());
			tempPdf = tempFile;
			LOG.error("tempPdf "+tempPdf.getAbsolutePath());
		}
		
		return tempPdf;
		
	}
	
	private void createNewDocument(File source) throws DuplicateUuidException,InsufficientNameUniquenessException, 
	PrivilegeException,StorageLimitException, InvalidContentException, IOException {
		Document doc = new Document();
		
		doc.setName(mergePdfName);
		
		
		if(!"".equals(mergePdfDescription)){
			doc.setDescription(mergePdfDescription);
		}
		
		doc.setExtension("pdf");
		doc.setParent(mergePdfFolder);
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
	
	public MergePdfDocuments(ContentService cs) {
		super();
		this.cs = cs;
	}

	public void onSave(MessageContainer messages) {
	}

	public void validate(MessageContainer messages){
		
	/*	if(inputPdfs!=null && inputPdfs.length == 1){
			messages.addError("InputPdfs","inputpdf.mindocument.error");
			LOG.error("Input pdfs must be more than one");
		}
		
		if(inputPdfs!=null && inputPdfs.length > 1){
			// check extension of all input documents
			boolean status = true;
			for(Long doc: inputPdfs){
				
				try{
					if(!fileExtension(cs.getInternalFilename(doc)).equalsIgnoreCase("pdf")){
						status = false;
						break;
					}						
				}
				catch(InvalidContentException e){
					error=e.getMessage();
					LOG.error(e.getMessage());
				}				
			}			
			if(status == false){
				messages.addError("InputPdfs","inputpdf.doctype.error");
				LOG.error("The input type is not PDF");
			}
		}*/
	}
	
	public String fileExtension(String fileName){
		return FilenameUtils.getExtension(fileName);
	}

	@Input(required = Required.ALWAYS)
	@Name("InputPdfs")
	@DocumentDataType
	public void setInputPdfs(Long[] val) {
		this.inputPdfs = val;
	}

	@Input(required = Required.ALWAYS)
	@Name("MergePdfFolder")
	@FolderDataType
	public void setMergePdfFolder(Long val) {
		this.mergePdfFolder = val;
	}

	@Input(required = Required.ALWAYS)
	@Name("MergePdfName")
	public void setMergePdfName(String val) {
		this.mergePdfName = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("MergePdfDescription")
	public void setMergePdfDescription(String val) {
		this.mergePdfDescription = val;
	}

	@Input(required = Required.ALWAYS)
	@Name("AddPageNumber")
	public void setMergePdfAddPageNumber(Boolean val) {
		this.mergePdfAddPageNumber = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("PageNoStyle")
	public void setMergePdfPageNoStyle(Long val) {
		this.mergePdfPageNoStyle = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("StartNumberingFromPageNo")
	public void setMergePdfStartNumberingPageNo(Long val) {
		this.mergePdfStartNumberingPageNo = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("PageNumberFontType")
	public void setMergePdfPageNoFontType(String val) {
		this.mergePdfPageNoFontType = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("PageNumberBottomMargin")
	public void setMergePdfNumberingBottomMargin(Long val) {
		this.mergePdfNumberingBottomMargin = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("PageNumberRightMargin")
	public void setMergePdfNumberingRightMargin(Long val) {
		this.mergePdfNumberingRightMargin = val;
	}

	@Input(required = Required.OPTIONAL)
	@Name("PageNumberFontSize")
	public void setMergePdfFontSize(Double val) {
		this.mergePdfFontSize = val;
	}
	
	
	
	@Name("MergePDF")
	@DocumentDataType
	public Long getOutput() {
		return output;
	}

	@Name("Error")
	public String getError() {
		return error;
	}

}
