package swap.gui.node;

import javafx.beans.binding.NumberBinding;
import javafx.collections.ListChangeListener;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;

// Credit https://blog.e-zest.com/percent-width-for-tablecolumn-in-javafx-2-x-tableview/
// Works well
public class PercentTableView<S> extends StackPane { private TableView<S> table; @SuppressWarnings("rawtypes") public PercentTableView(){ this.table = new TableView<S>(); final GridPane grid = new GridPane(); this.table.getColumns().addListener(new ListChangeListener(){ @Override public void onChanged(javafx.collections.ListChangeListener.Change arg0) { grid.getColumnConstraints().clear(); ColumnConstraints[] arr1 = new ColumnConstraints[PercentTableView.this.table.getColumns().size()]; StackPane[] arr2 = new StackPane[PercentTableView.this.table.getColumns().size()]; int i=0; for(TableColumn column : PercentTableView.this.table.getColumns()){ PercentTableColumn col = (PercentTableColumn)column; ColumnConstraints consta = new ColumnConstraints(); consta.setPercentWidth(col.getPercentWidth()); StackPane sp = new StackPane(); if(i==0){ /*Quick fix for not showing the horizantal scroll bar.*/ NumberBinding diff = sp.widthProperty().subtract(3.75); column.prefWidthProperty().bind(diff); }else{ column.prefWidthProperty().bind(sp.widthProperty()); } arr1[i] = consta; arr2[i] = sp; i++; } grid.getColumnConstraints().addAll(arr1); grid.addRow(0, arr2); } }); getChildren().addAll(grid,table); } public TableView<S> getTableView(){ return this.table; } }
