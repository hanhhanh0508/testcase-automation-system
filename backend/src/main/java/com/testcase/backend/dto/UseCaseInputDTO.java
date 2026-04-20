package com.testcase.backend.dto;

public class UseCaseInputDTO {

    // Người dùng nhập text mô tả use case
    private String useCaseText;

    // Tên diagram (tùy chọn)
    private String diagramName;

    // Định dạng: TEXT, XMI, PLANTUML, DRAWIO
    private String sourceFormat;

    public UseCaseInputDTO() {
    }

    public String getUseCaseText() {
        return useCaseText;
    }

    public void setUseCaseText(String useCaseText) {
        this.useCaseText = useCaseText;
    }

    public String getDiagramName() {
        return diagramName;
    }

    public void setDiagramName(String diagramName) {
        this.diagramName = diagramName;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }
}
