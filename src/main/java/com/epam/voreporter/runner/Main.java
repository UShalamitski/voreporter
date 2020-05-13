package com.epam.voreporter.runner;

import com.epam.voreporter.service.ReportService;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {

    public static void main(String[] args){
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("com/epam/voreporter/context.xml");
        ReportService reportService = context.getBean(ReportService.class);
        reportService.openReportInOutlook();
    }
}
