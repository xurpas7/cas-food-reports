/**
 * Waste Inventory Reports Main Class
 * @author Michael T. Cuison
 * @started 2019.06.07
 */

package org.rmj.cas.food.reports.classes;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRResultSetDataSource;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.view.JasperViewer;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.rmj.appdriver.GLogger;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.ShowMessageFX;
import org.rmj.appdriver.iface.GReport;
import org.rmj.replication.utility.LogWrapper;

public class Purchases implements GReport{
    private GRider _instance;
    private boolean _preview = true;
    private String _message = "";
    private LinkedList _rptparam = null;
    private JasperPrint _jrprint = null;
    private LogWrapper logwrapr = new LogWrapper(this.getClass().getName(), "Purchases.log");
    
    private double xOffset = 0; 
    private double yOffset = 0;
    
    public Purchases(){
        _rptparam = new LinkedList();
        _rptparam.add("store.report.id");
        _rptparam.add("store.report.no");
        _rptparam.add("store.report.name");
        _rptparam.add("store.report.jar");
        _rptparam.add("store.report.class");
        _rptparam.add("store.report.is_save");
        _rptparam.add("store.report.is_log");
        
        _rptparam.add("store.report.criteria.presentation");
        _rptparam.add("store.report.criteria.branch");      
        _rptparam.add("store.report.criteria.group");        
        _rptparam.add("store.report.criteria.datefrom");     
        _rptparam.add("store.report.criteria.datethru");     
        _rptparam.add("store.report.criteria.supplier");
    }
    
    @Override
    public void setGRider(Object foApp) {
        _instance = (GRider) foApp;
    }
    
    @Override
    public void hasPreview(boolean show) {
        _preview = show;
    }

    @Override
    public boolean getParam() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("PurchasesCriteria.fxml"));
        fxmlLoader.setLocation(getClass().getResource("PurchasesCriteria.fxml"));

        PurchasesCriteriaController instance = new PurchasesCriteriaController();
        instance.setGRider(_instance);
        instance.singleDayOnly(false);
        
        try {
            
            fxmlLoader.setController(instance);
            Parent parent = fxmlLoader.load();
            Stage stage = new Stage();

            /*SET FORM MOVABLE*/
            parent.setOnMousePressed(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    xOffset = event.getSceneX();
                    yOffset = event.getSceneY();
                }
            });
            parent.setOnMouseDragged(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    stage.setX(event.getScreenX() - xOffset);
                    stage.setY(event.getScreenY() - yOffset);
                }
            });
            /*END SET FORM MOVABLE*/

            Scene scene = new Scene(parent);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setAlwaysOnTop(true);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e) {
            ShowMessageFX.Error(e.getMessage(), DailyProduction.class.getSimpleName(), "Please inform MIS Department.");
            System.exit(1);
        }
        
        if (!instance.isCancelled()){            
            System.setProperty("store.default.debug", "true");
            System.setProperty("store.report.criteria.presentation", instance.Presentation());
            System.setProperty("store.report.criteria.datefrom", instance.getDateFrom());
            System.setProperty("store.report.criteria.datethru", instance.getDateTo());
            System.setProperty("store.report.criteria.supplier", instance.getSupplier());
            
            System.setProperty("store.report.criteria.branch", "");
            System.setProperty("store.report.criteria.group", "");
            return true;
        }
        return false;
    }
    
    @Override
    public boolean processReport() {
        boolean bResult = false;
        
        //Get the criteria as extracted from getParam()
        if(System.getProperty("store.report.criteria.presentation").equals("0")){
            System.setProperty("store.report.no", "1");
        }else if(System.getProperty("store.report.criteria.group").equalsIgnoreCase("sBinNamex")) {
            System.setProperty("store.report.no", "3");
        }else if(System.getProperty("store.report.criteria.group").equalsIgnoreCase("sInvTypCd")) {
            System.setProperty("store.report.no", "4");
        }else{
            System.setProperty("store.report.no", "2");
        }
        
        //Load the jasper report to be use by this object
        String lsSQL = "SELECT sFileName, sReportHd" + 
                      " FROM xxxReportDetail" + 
                      " WHERE sReportID = " + SQLUtil.toSQL(System.getProperty("store.report.id")) +
                        " AND nEntryNox = " + SQLUtil.toSQL(System.getProperty("store.report.no"));
        
        //Check if in debug mode...
        if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){
            System.out.println(System.getProperty("store.report.class") + ".processReport: " + lsSQL);
        }
        
        ResultSet loRS = _instance.executeQuery(lsSQL);
        
        try {
            if(!loRS.next()){
                _message = "Invalid report was detected...";
                closeReport();
                return false;
            }
            System.setProperty("store.report.file", loRS.getString("sFileName"));
            System.setProperty("store.report.header", loRS.getString("sReportHd"));
            
            switch(Integer.valueOf(System.getProperty("store.report.no"))){
                case 1:
                    bResult = printSummary();
                    break;
                case 2: 
                    bResult = printDetail();
            }
            
            if(bResult){
                if(System.getProperty("store.report.is_log").equalsIgnoreCase("true")){
                    logReport();
                }
                JasperViewer jv = new JasperViewer(_jrprint, false);     
                jv.setVisible(true);                
            }
            
        } catch (SQLException ex) {
            _message = ex.getMessage();
            //Check if in debug mode...
            if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){
                ex.printStackTrace();
            }            
            GLogger.severe(System.getProperty("store.report.class"), "processReport", ExceptionUtils.getStackTrace(ex));
            
            closeReport();
            return false;
        }
        
        closeReport();
        return true;
    }

    @Override
    public void list() {
        _rptparam.forEach(item->System.out.println(item));
    }
    
    private boolean printSummary(){
        String lsSQL = getReportSQL();
        String lsCondition = "";
        String lsDate = "";
        
        if (!System.getProperty("store.report.criteria.datefrom").equals("") &&
                !System.getProperty("store.report.criteria.datethru").equals("")){

            lsDate = SQLUtil.toSQL(System.getProperty("store.report.criteria.datefrom")) + " AND " +
                        SQLUtil.toSQL(System.getProperty("store.report.criteria.datethru"));
            
            lsCondition = lsDate;
            
            lsSQL = MiscUtil.addCondition(lsSQL, "a.dTransact BETWEEN " + lsCondition);
        }
        
        if (!System.getProperty("store.report.criteria.supplier").equals("")){
            lsCondition = "a.sSupplier = " + SQLUtil.toSQL(System.getProperty("store.report.criteria.supplier"));
            
            lsSQL = MiscUtil.addCondition(lsSQL, lsCondition);
        }

        System.out.println(lsSQL);
        ResultSet rs = _instance.executeQuery(lsSQL);
        
        //Convert the data-source to JasperReport data-source
        JRResultSetDataSource jrRS = new JRResultSetDataSource(rs);
        
        //Create the parameter
        Map<String, Object> params = new HashMap<>();
        params.put("sCompnyNm", _instance.getClientName());  
        params.put("sBranchNm", _instance.getBranchName());
        params.put("sAddressx", _instance.getAddress() + " " + _instance.getTownName() + ", " + _instance.getProvince());      
        params.put("sReportNm", System.getProperty("store.report.header"));      
        params.put("sReportDt", !lsDate.equals("") ? lsDate.replace("AND", "to").replace("'", "") : "");
        params.put("sPrintdBy", _instance.getUserID());
        
        try {
            _jrprint = JasperFillManager.fillReport(_instance.getReportPath() + 
                                                    System.getProperty("store.report.file"),
                                                    params, 
                                                    jrRS);
        } catch (JRException ex) {
            Logger.getLogger(DailyProduction.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return true;
    }
    
    private boolean printDetail(){
        String lsSQL = getReportSQL();
        String lsCondition = "";
        String lsDate = "";
        
        if (!System.getProperty("store.report.criteria.datefrom").equals("") &&
                !System.getProperty("store.report.criteria.datethru").equals("")){

            lsDate = SQLUtil.toSQL(System.getProperty("store.report.criteria.datefrom")) + " AND " +
                        SQLUtil.toSQL(System.getProperty("store.report.criteria.datethru"));
            
            lsCondition = lsDate;
            
            lsSQL = MiscUtil.addCondition(lsSQL, "a.dTransact BETWEEN " + lsCondition);
        }
        
        if (!System.getProperty("store.report.criteria.supplier").equals("")){
            lsCondition = "a.sSupplier = " + SQLUtil.toSQL(System.getProperty("store.report.criteria.supplier"));
            
            lsSQL = MiscUtil.addCondition(lsSQL, lsCondition);
        }

        System.out.println(lsSQL);
        ResultSet rs = _instance.executeQuery(lsSQL);
        
        //Convert the data-source to JasperReport data-source
        JRResultSetDataSource jrRS = new JRResultSetDataSource(rs);
        
        //Create the parameter
        Map<String, Object> params = new HashMap<>();
        params.put("sCompnyNm", _instance.getClientName());  
        params.put("sBranchNm", _instance.getBranchName());
        params.put("sAddressx", _instance.getAddress() + " " + _instance.getTownName() + ", " + _instance.getProvince());      
        params.put("sReportNm", System.getProperty("store.report.header"));      
        params.put("sReportDt", !lsDate.equals("") ? lsDate.replace("AND", "to").replace("'", "") : "");
        params.put("sPrintdBy", _instance.getUserID());
        
        try {
            _jrprint = JasperFillManager.fillReport(_instance.getReportPath() + 
                                                    System.getProperty("store.report.file"),
                                                    params, 
                                                    jrRS);
        } catch (JRException ex) {
            Logger.getLogger(DailyProduction.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return true;
    }
    
    private void closeReport(){
        _rptparam.forEach(item->System.clearProperty((String) item));
        System.clearProperty("store.report.file");
        System.clearProperty("store.report.header");
    }
    
    private void logReport(){
        _rptparam.forEach(item->System.clearProperty((String) item));
        System.clearProperty("store.report.file");
        System.clearProperty("store.report.header");
    }
    
    private String getReportSQL(){
        return "SELECT" +
                    "  c.sBarCodex `sField01`" +
                    ", CONCAT(c.sDescript, IF(IFNULL(d.sDescript, '') = '', '', CONCAT(' / ', d.sDescript)), IF(IFNULL(e.sDescript, '') = '', '', CONCAT(' / ', e.sDescript)), IF(IFNULL(f.sMeasurNm, '') = '', '', CONCAT(' / ', f.sMeasurNm))) `sField02`" +
                    ", b.nQuantity `nField01`" +
                    ", b.nUnitPrce `lField01`" +
                    ", DATE_FORMAT(a.dTransact, '%Y-%m-%d') `sField04`" +
                    ", g.sClientNm `sField03`" + 
                    ", a.sReferNox `sField05`" +
                    ", CONCAT(IFNULL(h.sBranchNm, ''), '/', IFNULL(i.sDeptName, '')) `sField06`" +
                    ", a.sRemarksx `sField07`" +
                    ", DATE_FORMAT(a.dRefernce, '%Y-%m-%d') `sField08`" +
                " FROM PO_Receiving_Master a" +
                            " LEFT JOIN Client_Master g" + 
                                " ON a.sSupplier = g.sClientID" + 
                            " LEFT JOIN Branch h" +
                                " ON a.sBranchCd = h.sBranchCd" +
                            " LEFT JOIN Department i" +
                                " ON a.sDeptIDxx = i.sDeptIDxx" +
                        ", PO_Receiving_Detail b" +
                            " LEFT JOIN Inventory c" +
                                " ON b.sStockIDx = c.sStockIDx" +
                            " LEFT JOIN Model d" +
                                " ON c.sModelCde = d.sModelCde" + 
                            " LEFT JOIN Brand e" + 
                                " ON c.sBrandCde = e.sBrandCde" + 
                            " LEFT JOIN Measure f" +
                                " ON c.sMeasurID = f.sMeasurID" + 
                " WHERE a.sTransNox = b.sTransNox" +                
                    " AND LEFT(a.sTransNox, 4) = " + SQLUtil.toSQL(_instance.getBranchCode()) + 
                    " AND a.cTranStat <> '3'" + 
                " ORDER BY sField08, sField03, sField02";
    }
    
    private String getReportSQLSum(){
        return "SELECT" +
                    " g.sClientNm `sField03`" +
                    ", a.sReferNox `sField05`" +
                    ", CONCAT(IFNULL(h.sBranchNm, ''), '/', IFNULL(i.sDeptName, '')) `sField06`" +
                    ", DATE_FORMAT(a.dRefernce, '%Y-%m-%d') `sField08`" +
                    ", SUM(b.nQuantity * b.nUnitPrce) `lField01`" +
                " FROM PO_Receiving_Master a" + 
                        " LEFT JOIN Branch h" + 
                            " ON a.sBranchCd = h.sBranchCd" + 
                        " LEFT JOIN Department i" + 
                            " ON a.sDeptIDxx = i.sDeptIDxx" +
                    ", Client_Master g" +
                    ", PO_Receiving_Detail b" + 
                        " LEFT JOIN Inventory c" + 
                                " ON b.sStockIDx = c.sStockIDx" + 
                        " LEFT JOIN Model d" + 
                                " ON c.sModelCde = d.sModelCde" + 
                        " LEFT JOIN Brand e" + 
                                " ON c.sBrandCde = e.sBrandCde" + 
                        " LEFT JOIN Measure f" + 
                                " ON c.sMeasurID = f.sMeasurID" + 
                " WHERE a.sTransNox = b.sTransNox" + 
                    " AND a.`sSupplier` = g.`sClientID`" +
                    " AND LEFT(a.sTransNox, 4) = " + SQLUtil.toSQL(_instance.getBranchCode()) + 
                    " AND a.cTranStat <> '3'" + 
                " GROUP BY sField05, sField03" +
                " ORDER BY sField08, sField03, sField05, sField06";
    }
}
