package swap.gui.node;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.TableColumn;

// Credit https://blog.e-zest.com/percent-width-for-tablecolumn-in-javafx-2-x-tableview/
// Works well
public class PercentTableColumn<S, T> extends TableColumn<S, T> { private SimpleDoubleProperty percentWidth = new SimpleDoubleProperty(); public PercentTableColumn(String columnName){ super(columnName); } public SimpleDoubleProperty percentWidth() { return percentWidth; } public double getPercentWidth() { return percentWidth.get(); } public void setPercentWidth(double percentWidth) { this.percentWidth.set(percentWidth); } }
