/**
*  Business Engine Extension
*/

/****************************************************************************************
Extension Name: UpdDrawMes
Type : ExtendM3Transaction
Script Author: Prabodha Amarapema
Date: 2023-09-07
 
Description:
      Update Drawing measurement  in PDS081
         
Revision History:
Name                            Date                   Version          Description of Changes
Prabodha Amarapema	         	2023-09-07					   1.0              Initial Version
******************************************************************************************/
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

public class UpdDrawMes extends ExtendM3Transaction {
  private final MIAPI mi;
  private final DatabaseAPI database
  private final ProgramAPI program
  private final MICallerAPI miCaller
  
  private validInput = true
  private String iPRNO,iSTRT,iDMID,iFACI,iMEVA, MEVA
  private int iCONO,iSQNU,DCCD
  
  
  public UpdDrawMes(MIAPI mi, DatabaseAPI database, ProgramAPI program, MICallerAPI miCaller) {
    this.mi = mi
    this.database = database
    this.program = program
    this.miCaller = miCaller
  }
  
  public void main() {
    iPRNO = (mi.inData.get("PRNO") == null || mi.inData.get("PRNO").trim().isEmpty()) ? "" : mi.inData.get("PRNO")
    iSTRT = (mi.inData.get("STRT") == null || mi.inData.get("STRT").trim().isEmpty()) ? "" : mi.inData.get("STRT")
    iDMID = (mi.inData.get("DMID") == null || mi.inData.get("DMID").trim().isEmpty()) ? "" : mi.inData.get("DMID")
    iMEVA = (mi.inData.get("MEVA") == null || mi.inData.get("MEVA").trim().isEmpty()) ? "" : mi.inData.get("MEVA")
    iSQNU = (mi.inData.get("SQNU") == null || mi.inData.get("SQNU").trim().isEmpty()) ? 0 : mi.inData.get("SQNU") as Integer
    iCONO = (mi.inData.get("CONO") == null || mi.inData.get("CONO").trim().isEmpty()) ? program.LDAZD.CONO as Integer : mi.inData.get("CONO") as Integer
    iFACI = (mi.inData.get("FACI") == null || mi.inData.get("FACI").trim().isEmpty()) ? "" : mi.inData.get("FACI")
    
    validateInput()
    
    if(validInput){
      getNumberofDecimaPlaces()
      updaterecord()
    } 
  
  }
  
  public validateInput(){
    if(!iMEVA.isNumber()){
      mi.error("Incorrect syntax")
      validInput=false
      return false
    }
    
    Map<String, String> paramsFACI = ["CONO": iCONO.toString().trim(), "FACI": iFACI.toString().trim()]
    Closure<?> callbackFACI = {
        Map < String,
        String > response ->
        if (response.FACI == null) {
            mi.error("Invalid Facility " + iFACI)
            validInput = false
            return 
        }
    }
    miCaller.call("CRS008MI", "Get", paramsFACI, callbackFACI) 
    
    Map<String, String> paramsPRNO = ["CONO": iCONO.toString().trim(), "FACI": iFACI.toString().trim(), "STRT" :iSTRT.toString().trim(), PRNO : iPRNO.toString().trim() ]
    Closure<?> callbackPRNO = {
        Map < String,
        String > response ->
        if (response.PRNO == null) {
            mi.error("Invalid PRNO " + iPRNO)
            validInput = false
            return 
        }
    }
    miCaller.call("PDS001MI", "Get", paramsPRNO, callbackPRNO) 
    
    
    if(iMEVA.contains(".")){
      // Split the input text at the decimal point
      def parts = iMEVA.split("\\.")
      // Check the integer part (part before the decimal point)
      if (parts[0].isNumber() && parts[0].length() > 9) {
          mi.error("The entered value is too large")
          validInput=false
          return false
      }else{
        String number = parts[0].padLeft(9, '0')
        String decimalComponent = parts[1].padRight(6,'0')
        
        MEVA = number + decimalComponent
      
      }
    }else{
      if(iMEVA.trim().length() > 9){
         mi.error("The entered value is too large" )
         validInput=false
         return false
      }else{
        while (iMEVA.trim().length() < 9) {
           iMEVA = "0" + iMEVA
        }
        MEVA = iMEVA.trim() + "000000"
      }
    }
  }
  
  public getNumberofDecimaPlaces()
  {
    DBAction query = database.table("MPDMEA").selection("QEDCCD").index("00").build()
    DBContainer container = query.getContainer()
     
    container.set("QECONO", iCONO)
    container.set("QEDMID", iDMID)
    
    if(query.read(container)) {
      DCCD = container.getInt("QEDCCD")
    }else{
      mi.error("Record does not Exist.")
      return
    }
  }
  
  /**
   *Update records to EXTHPD table
   * @params 
   * @return 
   */
  public updaterecord(){
    DBAction query = database.table("MPDPMM").selection("QGCHNO").index("00").build()
    DBContainer container = query.getContainer()
    
    container.set("QGCONO", iCONO)
    container.set("QGFACI", iFACI)
    container.set("QGPRNO", iPRNO)
    container.set("QGSTRT", iSTRT)
    container.set("QGSQNU", iSQNU)
    container.set("QGDMID", iDMID)
    
    if (query.read(container)) {
      query.readLock(container, updateCallBack)
    } else {
      mi.error("Record does not Exist.")
      return
    }
  }
  
  Closure < ? > updateCallBack = {
    LockedResult lockedResult ->
    int CHNO = lockedResult.get("QGCHNO")
  
    lockedResult.set("QGMEVA", MEVA)
    lockedResult.set("QGLMDT", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInteger())
    lockedResult.set("QGLMTS", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")).toInteger())
    lockedResult.set("QGCHNO", CHNO + 1)
    lockedResult.set("QGCHID", program.getUser())
    lockedResult.update()
  }
  
}