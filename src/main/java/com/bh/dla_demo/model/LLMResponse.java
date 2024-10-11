package com.bh.dla_demo.model;

import java.util.List;

public class LLMResponse {
    private List<BlankComponent> blankComponents;
    private SignaturePadText signaturePadText;
    private List<WorkflowStep> workflow;

    // Getters and setters

    public List<BlankComponent> getBlankComponents() {
        return blankComponents;
    }

    public void setBlankComponents(List<BlankComponent> blankComponents) {
        this.blankComponents = blankComponents;
    }

    public SignaturePadText getSignaturePadText() {
        return signaturePadText;
    }

    public void setSignaturePadText(SignaturePadText signaturePadText) {
        this.signaturePadText = signaturePadText;
    }

    public List<WorkflowStep> getWorkflow() {
        return workflow;
    }

    public void setWorkflow(List<WorkflowStep> workflow) {
        this.workflow = workflow;
    }
}
