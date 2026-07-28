// 이 파일은 scripts/generate-blueprint-registry.mjs가 생성한다. 직접 수정하지 않는다.
import type { ComponentType } from "react";
import { AdminWorkspaceLayout } from "../layouts/AdminWorkspaceLayout";
import { AnalyticsDashboardLayout } from "../layouts/AnalyticsDashboardLayout";
import { CommerceCatalogLayout } from "../layouts/CommerceCatalogLayout";
import { ContentStudioLayout } from "../layouts/ContentStudioLayout";
import { MasterDetailLayout } from "../layouts/MasterDetailLayout";
import { OperationsCockpitLayout } from "../layouts/OperationsCockpitLayout";
import { SettingsWorkbenchLayout } from "../layouts/SettingsWorkbenchLayout";
import { WorkflowStageLayout } from "../layouts/WorkflowStageLayout";
import { ExecutiveKpiDashboard } from "../dashboards/ExecutiveKpiDashboard";
import { OperationsHealthDashboard } from "../dashboards/OperationsHealthDashboard";
import { CommerceRevenueDashboard } from "../dashboards/CommerceRevenueDashboard";
import { ContentPerformanceDashboard } from "../dashboards/ContentPerformanceDashboard";
import { AdminGovernanceDashboard } from "../dashboards/AdminGovernanceDashboard";
import { ProjectDeliveryDashboard } from "../dashboards/ProjectDeliveryDashboard";
import { KanbanCollection } from "../collections/KanbanCollection";
import { TimelineCollection } from "../collections/TimelineCollection";
import { MediaGalleryCollection } from "../collections/MediaGalleryCollection";
import { EntityDirectory } from "../collections/EntityDirectory";
import { AlertInbox } from "../collections/AlertInbox";
import { CommerceProductGrid } from "../collections/CommerceProductGrid";
import { AuditLogTable } from "../collections/AuditLogTable";
import { CompactMetricTable } from "../collections/CompactMetricTable";
import { InfrastructureResourceDetail } from "../details/InfrastructureResourceDetail";
import { CommerceOrderDetail } from "../details/CommerceOrderDetail";
import { CustomerProfileDetail } from "../details/CustomerProfileDetail";
import { ContentArticleDetail } from "../details/ContentArticleDetail";
import { IncidentDetail } from "../details/IncidentDetail";
import { SettingsDetail } from "../details/SettingsDetail";
import { TypedDangerModal } from "../modals/TypedDangerModal";
import { BulkActionModal } from "../modals/BulkActionModal";
import { ImportDataModal } from "../modals/ImportDataModal";
import { ExportDataModal } from "../modals/ExportDataModal";
import { AssignOwnerModal } from "../modals/AssignOwnerModal";
import { ChangeStatusModal } from "../modals/ChangeStatusModal";
import { ScheduleActionModal } from "../modals/ScheduleActionModal";
import { PermissionMatrixModal } from "../modals/PermissionMatrixModal";
import { PayloadPreviewModal } from "../modals/PayloadPreviewModal";
import { DependencyImpactModal } from "../modals/DependencyImpactModal";
import { DuplicateResourceModal } from "../modals/DuplicateResourceModal";
import { ResourceProvisioningWizard } from "../workflows/ResourceProvisioningWizard";
import { DeploymentWorkflowWizard } from "../workflows/DeploymentWorkflowWizard";
import { ApprovalWorkflowWizard } from "../workflows/ApprovalWorkflowWizard";
import { PublishWorkflowWizard } from "../workflows/PublishWorkflowWizard";
import { DataImportWizard } from "../workflows/DataImportWizard";
import { UserOnboardingWizard } from "../workflows/UserOnboardingWizard";
import { SecurityCommandLayout } from "../layouts/SecurityCommandLayout";
import { SupportConsoleLayout } from "../layouts/SupportConsoleLayout";
import { FinanceLedgerLayout } from "../layouts/FinanceLedgerLayout";
import { LogisticsDispatchLayout } from "../layouts/LogisticsDispatchLayout";
import { InventoryWarehouseLayout } from "../layouts/InventoryWarehouseLayout";
import { BookingPlannerLayout } from "../layouts/BookingPlannerLayout";
import { EventOperationsLayout } from "../layouts/EventOperationsLayout";
import { LearningPortalLayout } from "../layouts/LearningPortalLayout";
import { HrPeopleOpsLayout } from "../layouts/HrPeopleOpsLayout";
import { DeveloperPlatformLayout } from "../layouts/DeveloperPlatformLayout";
import { AiStudioLayout } from "../layouts/AiStudioLayout";
import { IoTControlLayout } from "../layouts/IoTControlLayout";
import { RealEstatePortfolioLayout } from "../layouts/RealEstatePortfolioLayout";
import { KnowledgeBaseLayout } from "../layouts/KnowledgeBaseLayout";
import { SocialCommunityLayout } from "../layouts/SocialCommunityLayout";
import { MarketplaceOperationsLayout } from "../layouts/MarketplaceOperationsLayout";
import { BillingWorkspaceLayout } from "../layouts/BillingWorkspaceLayout";
import { TravelPlannerLayout } from "../layouts/TravelPlannerLayout";
import { LegalCaseLayout } from "../layouts/LegalCaseLayout";
import { MediaProductionLayout } from "../layouts/MediaProductionLayout";
import { SecurityThreatDashboard } from "../dashboards/SecurityThreatDashboard";
import { SocOverviewDashboard } from "../dashboards/SocOverviewDashboard";
import { SupportSlaDashboard } from "../dashboards/SupportSlaDashboard";
import { CustomerSuccessDashboard } from "../dashboards/CustomerSuccessDashboard";
import { FinanceCashflowDashboard } from "../dashboards/FinanceCashflowDashboard";
import { BillingRevenueDashboard } from "../dashboards/BillingRevenueDashboard";
import { InventoryTurnoverDashboard } from "../dashboards/InventoryTurnoverDashboard";
import { WarehouseCapacityDashboard } from "../dashboards/WarehouseCapacityDashboard";
import { LogisticsFleetDashboard } from "../dashboards/LogisticsFleetDashboard";
import { DeliveryPerformanceDashboard } from "../dashboards/DeliveryPerformanceDashboard";
import { BookingOccupancyDashboard } from "../dashboards/BookingOccupancyDashboard";
import { EventAttendanceDashboard } from "../dashboards/EventAttendanceDashboard";
import { LearningProgressDashboard } from "../dashboards/LearningProgressDashboard";
import { CohortPerformanceDashboard } from "../dashboards/CohortPerformanceDashboard";
import { HrWorkforceDashboard } from "../dashboards/HrWorkforceDashboard";
import { RecruitingPipelineDashboard } from "../dashboards/RecruitingPipelineDashboard";
import { DeveloperUsageDashboard } from "../dashboards/DeveloperUsageDashboard";
import { ApiReliabilityDashboard } from "../dashboards/ApiReliabilityDashboard";
import { AiModelOpsDashboard } from "../dashboards/AiModelOpsDashboard";
import { PromptAnalyticsDashboard } from "../dashboards/PromptAnalyticsDashboard";
import { IoTDeviceFleetDashboard } from "../dashboards/IoTDeviceFleetDashboard";
import { EnergyUsageDashboard } from "../dashboards/EnergyUsageDashboard";
import { RealEstatePortfolioDashboard } from "../dashboards/RealEstatePortfolioDashboard";
import { PropertyOccupancyDashboard } from "../dashboards/PropertyOccupancyDashboard";
import { CommunityEngagementDashboard } from "../dashboards/CommunityEngagementDashboard";
import { MarketplaceLiquidityDashboard } from "../dashboards/MarketplaceLiquidityDashboard";
import { TravelOperationsDashboard } from "../dashboards/TravelOperationsDashboard";
import { LegalMatterDashboard } from "../dashboards/LegalMatterDashboard";
import { MediaPipelineDashboard } from "../dashboards/MediaPipelineDashboard";
import { KnowledgeHealthDashboard } from "../dashboards/KnowledgeHealthDashboard";
import { ThreatEventStream } from "../collections/ThreatEventStream";
import { VulnerabilityMatrix } from "../collections/VulnerabilityMatrix";
import { SupportTicketInbox } from "../collections/SupportTicketInbox";
import { CustomerHealthBoard } from "../collections/CustomerHealthBoard";
import { TransactionLedger } from "../collections/TransactionLedger";
import { InvoiceCollection } from "../collections/InvoiceCollection";
import { InventorySkuMatrix } from "../collections/InventorySkuMatrix";
import { WarehouseBinExplorer } from "../collections/WarehouseBinExplorer";
import { ShipmentTrackingBoard } from "../collections/ShipmentTrackingBoard";
import { RouteStopTimeline } from "../collections/RouteStopTimeline";
import { ReservationCalendar } from "../collections/ReservationCalendar";
import { VenueSeatMap } from "../collections/VenueSeatMap";
import { CourseCatalogGrid } from "../collections/CourseCatalogGrid";
import { LearnerRoster } from "../collections/LearnerRoster";
import { EmployeeDirectoryGrid } from "../collections/EmployeeDirectoryGrid";
import { CandidatePipeline } from "../collections/CandidatePipeline";
import { ApiEndpointCatalog } from "../collections/ApiEndpointCatalog";
import { DeploymentEnvironmentMatrix } from "../collections/DeploymentEnvironmentMatrix";
import { ModelRegistryCollection } from "../collections/ModelRegistryCollection";
import { PromptLibraryGrid } from "../collections/PromptLibraryGrid";
import { DeviceTopologyList } from "../collections/DeviceTopologyList";
import { SensorReadingTable } from "../collections/SensorReadingTable";
import { PropertyListingGrid } from "../collections/PropertyListingGrid";
import { TenantDirectory } from "../collections/TenantDirectory";
import { CommunityFeed } from "../collections/CommunityFeed";
import { ModerationQueue } from "../collections/ModerationQueue";
import { VendorMarketplaceGrid } from "../collections/VendorMarketplaceGrid";
import { TripItineraryCollection } from "../collections/TripItineraryCollection";
import { LegalCaseDocket } from "../collections/LegalCaseDocket";
import { AssetProductionBoard } from "../collections/AssetProductionBoard";
import { ThreatIncidentDetail } from "../details/ThreatIncidentDetail";
import { VulnerabilityDetail } from "../details/VulnerabilityDetail";
import { SupportTicketDetail } from "../details/SupportTicketDetail";
import { CustomerSuccessDetail } from "../details/CustomerSuccessDetail";
import { TransactionDetail } from "../details/TransactionDetail";
import { InvoiceDetail } from "../details/InvoiceDetail";
import { InventoryItemDetail } from "../details/InventoryItemDetail";
import { ShipmentDetail } from "../details/ShipmentDetail";
import { ReservationDetail } from "../details/ReservationDetail";
import { EventDetail } from "../details/EventDetail";
import { CourseDetail } from "../details/CourseDetail";
import { LearnerDetail } from "../details/LearnerDetail";
import { EmployeeProfileDetail } from "../details/EmployeeProfileDetail";
import { CandidateDetail } from "../details/CandidateDetail";
import { ApiProductDetail } from "../details/ApiProductDetail";
import { DeploymentDetail } from "../details/DeploymentDetail";
import { ModelDetail } from "../details/ModelDetail";
import { PromptDetail } from "../details/PromptDetail";
import { DeviceDetail } from "../details/DeviceDetail";
import { PropertyDetail } from "../details/PropertyDetail";
import { CommunityMemberDetail } from "../details/CommunityMemberDetail";
import { VendorDetail } from "../details/VendorDetail";
import { TripDetail } from "../details/TripDetail";
import { LegalMatterDetail } from "../details/LegalMatterDetail";
import { MediaAssetDetail } from "../details/MediaAssetDetail";
import { KnowledgeArticleDetail } from "../details/KnowledgeArticleDetail";
import { AcknowledgeAlertModal } from "../modals/AcknowledgeAlertModal";
import { EscalateIncidentModal } from "../modals/EscalateIncidentModal";
import { MergeTicketsModal } from "../modals/MergeTicketsModal";
import { SendReplyModal } from "../modals/SendReplyModal";
import { IssueRefundModal } from "../modals/IssueRefundModal";
import { CapturePaymentModal } from "../modals/CapturePaymentModal";
import { AdjustInventoryModal } from "../modals/AdjustInventoryModal";
import { TransferStockModal } from "../modals/TransferStockModal";
import { ReassignShipmentModal } from "../modals/ReassignShipmentModal";
import { DeliveryExceptionModal } from "../modals/DeliveryExceptionModal";
import { RescheduleBookingModal } from "../modals/RescheduleBookingModal";
import { SeatAssignmentModal } from "../modals/SeatAssignmentModal";
import { EnrollLearnerModal } from "../modals/EnrollLearnerModal";
import { GradeSubmissionModal } from "../modals/GradeSubmissionModal";
import { TimeOffRequestModal } from "../modals/TimeOffRequestModal";
import { CompensationChangeModal } from "../modals/CompensationChangeModal";
import { RotateApiKeyModal } from "../modals/RotateApiKeyModal";
import { PromoteDeploymentModal } from "../modals/PromoteDeploymentModal";
import { ModelEvaluationModal } from "../modals/ModelEvaluationModal";
import { PromptTestModal } from "../modals/PromptTestModal";
import { DeviceCommandModal } from "../modals/DeviceCommandModal";
import { FirmwareUpdateModal } from "../modals/FirmwareUpdateModal";
import { PropertyInquiryModal } from "../modals/PropertyInquiryModal";
import { LeaseRenewalModal } from "../modals/LeaseRenewalModal";
import { ModerateContentModal } from "../modals/ModerateContentModal";
import { VendorPayoutModal } from "../modals/VendorPayoutModal";
import { TripChangeModal } from "../modals/TripChangeModal";
import { LegalHoldModal } from "../modals/LegalHoldModal";
import { PublishAssetModal } from "../modals/PublishAssetModal";
import { KnowledgeMergeModal } from "../modals/KnowledgeMergeModal";
import { IncidentResponseWizard } from "../workflows/IncidentResponseWizard";
import { VulnerabilityRemediationWizard } from "../workflows/VulnerabilityRemediationWizard";
import { TicketEscalationWizard } from "../workflows/TicketEscalationWizard";
import { CustomerRenewalWizard } from "../workflows/CustomerRenewalWizard";
import { PaymentReconciliationWizard } from "../workflows/PaymentReconciliationWizard";
import { SubscriptionUpgradeWizard } from "../workflows/SubscriptionUpgradeWizard";
import { StockReplenishmentWizard } from "../workflows/StockReplenishmentWizard";
import { ShipmentExceptionWizard } from "../workflows/ShipmentExceptionWizard";
import { ReservationSetupWizard } from "../workflows/ReservationSetupWizard";
import { EventLaunchWizard } from "../workflows/EventLaunchWizard";
import { CoursePublishingWizard } from "../workflows/CoursePublishingWizard";
import { EmployeeOnboardingWizard } from "../workflows/EmployeeOnboardingWizard";
import { CandidateHiringWizard } from "../workflows/CandidateHiringWizard";
import { ApiProductLaunchWizard } from "../workflows/ApiProductLaunchWizard";
import { ReleasePromotionWizard } from "../workflows/ReleasePromotionWizard";
import { ModelDeploymentWizard } from "../workflows/ModelDeploymentWizard";
import { DeviceProvisioningWizard } from "../workflows/DeviceProvisioningWizard";
import { PropertyListingWizard } from "../workflows/PropertyListingWizard";
import { VendorOnboardingWizard } from "../workflows/VendorOnboardingWizard";
import { LegalReviewWizard } from "../workflows/LegalReviewWizard";
import { MediaPublishingWizard } from "../workflows/MediaPublishingWizard";
import { KnowledgeApprovalWizard } from "../workflows/KnowledgeApprovalWizard";
import { DynamicSchemaForm } from "../forms/DynamicSchemaForm";
import { SectionedSettingsForm } from "../forms/SectionedSettingsForm";
import { InlineQuickEditForm } from "../forms/InlineQuickEditForm";
import { QueryBuilderForm } from "../forms/QueryBuilderForm";
import { FilterRuleBuilder } from "../forms/FilterRuleBuilder";
import { PricingPlanForm } from "../forms/PricingPlanForm";
import { CheckoutAddressForm } from "../forms/CheckoutAddressForm";
import { SupportReplyComposer } from "../forms/SupportReplyComposer";
import { ContentEditorForm } from "../forms/ContentEditorForm";
import { MetadataEditorForm } from "../forms/MetadataEditorForm";
import { ApiRequestBuilderForm } from "../forms/ApiRequestBuilderForm";
import { SecretReferenceForm } from "../forms/SecretReferenceForm";
import { ScheduleRuleForm } from "../forms/ScheduleRuleForm";
import { PermissionPolicyForm } from "../forms/PermissionPolicyForm";
import { NotificationPreferenceForm } from "../forms/NotificationPreferenceForm";
import { SurveyBuilderForm } from "../forms/SurveyBuilderForm";
import { LocalizationForm } from "../forms/LocalizationForm";
import { ThemeConfiguratorForm } from "../forms/ThemeConfiguratorForm";
import { GlobalCommandBar } from "../actions/GlobalCommandBar";
import { DenseAdminToolbar } from "../actions/DenseAdminToolbar";
import { ProductHeroActions } from "../actions/ProductHeroActions";
import { ResourceQuickActions } from "../actions/ResourceQuickActions";
import { BulkSelectionToolbar } from "../actions/BulkSelectionToolbar";
import { InlineRowActions } from "../actions/InlineRowActions";
import { SplitButtonActions } from "../actions/SplitButtonActions";
import { FloatingCreateAction } from "../actions/FloatingCreateAction";
import { StatusTransitionBar } from "../actions/StatusTransitionBar";
import { ReviewDecisionBar } from "../actions/ReviewDecisionBar";
import { PaginationToolbar } from "../actions/PaginationToolbar";
import { ViewModeSwitcher } from "../actions/ViewModeSwitcher";
import { FilterChipBar } from "../actions/FilterChipBar";
import { SavedViewBar } from "../actions/SavedViewBar";
import { ShareExportToolbar } from "../actions/ShareExportToolbar";
import { DangerZoneActions } from "../actions/DangerZoneActions";
import { IconRailNavigation } from "../navigation/IconRailNavigation";
import { CollapsibleSidebarNavigation } from "../navigation/CollapsibleSidebarNavigation";
import { WorkspaceTopNavigation } from "../navigation/WorkspaceTopNavigation";
import { BreadcrumbTrail } from "../navigation/BreadcrumbTrail";
import { TabbedSectionNavigation } from "../navigation/TabbedSectionNavigation";
import { StepperNavigation } from "../navigation/StepperNavigation";
import { CommandPalette } from "../navigation/CommandPalette";
import { MegaMenuNavigation } from "../navigation/MegaMenuNavigation";
import { ContextSwitcherNavigation } from "../navigation/ContextSwitcherNavigation";
import { MobileBottomNavigation } from "../navigation/MobileBottomNavigation";
import { AdminTreeNavigation } from "../navigation/AdminTreeNavigation";
import { DocsNavigation } from "../navigation/DocsNavigation";
import { CalendarNavigation } from "../navigation/CalendarNavigation";
import { ProductSubnav } from "../navigation/ProductSubnav";
import { IllustratedEmptyState } from "../feedback/IllustratedEmptyState";
import { PermissionDeniedState } from "../feedback/PermissionDeniedState";
import { OfflineState } from "../feedback/OfflineState";
import { MaintenanceState } from "../feedback/MaintenanceState";
import { RateLimitState } from "../feedback/RateLimitState";
import { DataLoadErrorState } from "../feedback/DataLoadErrorState";
import { SearchNoResultsState } from "../feedback/SearchNoResultsState";
import { ImportSuccessState } from "../feedback/ImportSuccessState";
import { OperationFailedState } from "../feedback/OperationFailedState";
import { SkeletonDashboardState } from "../feedback/SkeletonDashboardState";
import { TableLoadingState } from "../feedback/TableLoadingState";
import { PartialDataWarning } from "../feedback/PartialDataWarning";
import { UnsavedChangesBanner } from "../feedback/UnsavedChangesBanner";
import { FirstRunOnboardingState } from "../feedback/FirstRunOnboardingState";
import { TerminalGreenTheme } from "../themes/TerminalGreenTheme";
import { GlassAuroraTheme } from "../themes/GlassAuroraTheme";
import { EditorialPaperTheme } from "../themes/EditorialPaperTheme";
import { BrutalistMonoTheme } from "../themes/BrutalistMonoTheme";
import { EnterpriseBlueTheme } from "../themes/EnterpriseBlueTheme";
import { WarmCommerceTheme } from "../themes/WarmCommerceTheme";
import { NeonCyberTheme } from "../themes/NeonCyberTheme";
import { SoftPastelTheme } from "../themes/SoftPastelTheme";
import { OceanDataTheme } from "../themes/OceanDataTheme";
import { SunsetOperationsTheme } from "../themes/SunsetOperationsTheme";
import { ForestSaaSTheme } from "../themes/ForestSaaSTheme";
import { MonochromeProTheme } from "../themes/MonochromeProTheme";
import { LavenderCreatorTheme } from "../themes/LavenderCreatorTheme";
import { CrimsonSecurityTheme } from "../themes/CrimsonSecurityTheme";
import { SandstoneFinanceTheme } from "../themes/SandstoneFinanceTheme";
import { HighContrastAccessibilityTheme } from "../themes/HighContrastAccessibilityTheme";

export const BLUEPRINT_COMPONENTS = {
  "admin-workspace-layout": AdminWorkspaceLayout,
  "analytics-dashboard-layout": AnalyticsDashboardLayout,
  "commerce-catalog-layout": CommerceCatalogLayout,
  "content-studio-layout": ContentStudioLayout,
  "master-detail-layout": MasterDetailLayout,
  "operations-cockpit-layout": OperationsCockpitLayout,
  "settings-workbench-layout": SettingsWorkbenchLayout,
  "workflow-stage-layout": WorkflowStageLayout,
  "executive-kpi-dashboard": ExecutiveKpiDashboard,
  "operations-health-dashboard": OperationsHealthDashboard,
  "commerce-revenue-dashboard": CommerceRevenueDashboard,
  "content-performance-dashboard": ContentPerformanceDashboard,
  "admin-governance-dashboard": AdminGovernanceDashboard,
  "project-delivery-dashboard": ProjectDeliveryDashboard,
  "kanban-collection": KanbanCollection,
  "timeline-collection": TimelineCollection,
  "media-gallery-collection": MediaGalleryCollection,
  "entity-directory": EntityDirectory,
  "alert-inbox": AlertInbox,
  "commerce-product-grid": CommerceProductGrid,
  "audit-log-table": AuditLogTable,
  "compact-metric-table": CompactMetricTable,
  "infrastructure-resource-detail": InfrastructureResourceDetail,
  "commerce-order-detail": CommerceOrderDetail,
  "customer-profile-detail": CustomerProfileDetail,
  "content-article-detail": ContentArticleDetail,
  "incident-detail": IncidentDetail,
  "settings-detail": SettingsDetail,
  "typed-danger-modal": TypedDangerModal,
  "bulk-action-modal": BulkActionModal,
  "import-data-modal": ImportDataModal,
  "export-data-modal": ExportDataModal,
  "assign-owner-modal": AssignOwnerModal,
  "change-status-modal": ChangeStatusModal,
  "schedule-action-modal": ScheduleActionModal,
  "permission-matrix-modal": PermissionMatrixModal,
  "payload-preview-modal": PayloadPreviewModal,
  "dependency-impact-modal": DependencyImpactModal,
  "duplicate-resource-modal": DuplicateResourceModal,
  "resource-provisioning-wizard": ResourceProvisioningWizard,
  "deployment-workflow-wizard": DeploymentWorkflowWizard,
  "approval-workflow-wizard": ApprovalWorkflowWizard,
  "publish-workflow-wizard": PublishWorkflowWizard,
  "data-import-wizard": DataImportWizard,
  "user-onboarding-wizard": UserOnboardingWizard,
  "security-command-layout": SecurityCommandLayout,
  "support-console-layout": SupportConsoleLayout,
  "finance-ledger-layout": FinanceLedgerLayout,
  "logistics-dispatch-layout": LogisticsDispatchLayout,
  "inventory-warehouse-layout": InventoryWarehouseLayout,
  "booking-planner-layout": BookingPlannerLayout,
  "event-operations-layout": EventOperationsLayout,
  "learning-portal-layout": LearningPortalLayout,
  "hr-people-ops-layout": HrPeopleOpsLayout,
  "developer-platform-layout": DeveloperPlatformLayout,
  "ai-studio-layout": AiStudioLayout,
  "iot-control-layout": IoTControlLayout,
  "real-estate-portfolio-layout": RealEstatePortfolioLayout,
  "knowledge-base-layout": KnowledgeBaseLayout,
  "social-community-layout": SocialCommunityLayout,
  "marketplace-operations-layout": MarketplaceOperationsLayout,
  "billing-workspace-layout": BillingWorkspaceLayout,
  "travel-planner-layout": TravelPlannerLayout,
  "legal-case-layout": LegalCaseLayout,
  "media-production-layout": MediaProductionLayout,
  "security-threat-dashboard": SecurityThreatDashboard,
  "soc-overview-dashboard": SocOverviewDashboard,
  "support-sla-dashboard": SupportSlaDashboard,
  "customer-success-dashboard": CustomerSuccessDashboard,
  "finance-cashflow-dashboard": FinanceCashflowDashboard,
  "billing-revenue-dashboard": BillingRevenueDashboard,
  "inventory-turnover-dashboard": InventoryTurnoverDashboard,
  "warehouse-capacity-dashboard": WarehouseCapacityDashboard,
  "logistics-fleet-dashboard": LogisticsFleetDashboard,
  "delivery-performance-dashboard": DeliveryPerformanceDashboard,
  "booking-occupancy-dashboard": BookingOccupancyDashboard,
  "event-attendance-dashboard": EventAttendanceDashboard,
  "learning-progress-dashboard": LearningProgressDashboard,
  "cohort-performance-dashboard": CohortPerformanceDashboard,
  "hr-workforce-dashboard": HrWorkforceDashboard,
  "recruiting-pipeline-dashboard": RecruitingPipelineDashboard,
  "developer-usage-dashboard": DeveloperUsageDashboard,
  "api-reliability-dashboard": ApiReliabilityDashboard,
  "ai-model-ops-dashboard": AiModelOpsDashboard,
  "prompt-analytics-dashboard": PromptAnalyticsDashboard,
  "iot-device-fleet-dashboard": IoTDeviceFleetDashboard,
  "energy-usage-dashboard": EnergyUsageDashboard,
  "real-estate-portfolio-dashboard": RealEstatePortfolioDashboard,
  "property-occupancy-dashboard": PropertyOccupancyDashboard,
  "community-engagement-dashboard": CommunityEngagementDashboard,
  "marketplace-liquidity-dashboard": MarketplaceLiquidityDashboard,
  "travel-operations-dashboard": TravelOperationsDashboard,
  "legal-matter-dashboard": LegalMatterDashboard,
  "media-pipeline-dashboard": MediaPipelineDashboard,
  "knowledge-health-dashboard": KnowledgeHealthDashboard,
  "threat-event-stream": ThreatEventStream,
  "vulnerability-matrix": VulnerabilityMatrix,
  "support-ticket-inbox": SupportTicketInbox,
  "customer-health-board": CustomerHealthBoard,
  "transaction-ledger": TransactionLedger,
  "invoice-collection": InvoiceCollection,
  "inventory-sku-matrix": InventorySkuMatrix,
  "warehouse-bin-explorer": WarehouseBinExplorer,
  "shipment-tracking-board": ShipmentTrackingBoard,
  "route-stop-timeline": RouteStopTimeline,
  "reservation-calendar": ReservationCalendar,
  "venue-seat-map": VenueSeatMap,
  "course-catalog-grid": CourseCatalogGrid,
  "learner-roster": LearnerRoster,
  "employee-directory-grid": EmployeeDirectoryGrid,
  "candidate-pipeline": CandidatePipeline,
  "api-endpoint-catalog": ApiEndpointCatalog,
  "deployment-environment-matrix": DeploymentEnvironmentMatrix,
  "model-registry-collection": ModelRegistryCollection,
  "prompt-library-grid": PromptLibraryGrid,
  "device-topology-list": DeviceTopologyList,
  "sensor-reading-table": SensorReadingTable,
  "property-listing-grid": PropertyListingGrid,
  "tenant-directory": TenantDirectory,
  "community-feed": CommunityFeed,
  "moderation-queue": ModerationQueue,
  "vendor-marketplace-grid": VendorMarketplaceGrid,
  "trip-itinerary-collection": TripItineraryCollection,
  "legal-case-docket": LegalCaseDocket,
  "asset-production-board": AssetProductionBoard,
  "threat-incident-detail": ThreatIncidentDetail,
  "vulnerability-detail": VulnerabilityDetail,
  "support-ticket-detail": SupportTicketDetail,
  "customer-success-detail": CustomerSuccessDetail,
  "transaction-detail": TransactionDetail,
  "invoice-detail": InvoiceDetail,
  "inventory-item-detail": InventoryItemDetail,
  "shipment-detail": ShipmentDetail,
  "reservation-detail": ReservationDetail,
  "event-detail": EventDetail,
  "course-detail": CourseDetail,
  "learner-detail": LearnerDetail,
  "employee-profile-detail": EmployeeProfileDetail,
  "candidate-detail": CandidateDetail,
  "api-product-detail": ApiProductDetail,
  "deployment-detail": DeploymentDetail,
  "model-detail": ModelDetail,
  "prompt-detail": PromptDetail,
  "device-detail": DeviceDetail,
  "property-detail": PropertyDetail,
  "community-member-detail": CommunityMemberDetail,
  "vendor-detail": VendorDetail,
  "trip-detail": TripDetail,
  "legal-matter-detail": LegalMatterDetail,
  "media-asset-detail": MediaAssetDetail,
  "knowledge-article-detail": KnowledgeArticleDetail,
  "acknowledge-alert-modal": AcknowledgeAlertModal,
  "escalate-incident-modal": EscalateIncidentModal,
  "merge-tickets-modal": MergeTicketsModal,
  "send-reply-modal": SendReplyModal,
  "issue-refund-modal": IssueRefundModal,
  "capture-payment-modal": CapturePaymentModal,
  "adjust-inventory-modal": AdjustInventoryModal,
  "transfer-stock-modal": TransferStockModal,
  "reassign-shipment-modal": ReassignShipmentModal,
  "delivery-exception-modal": DeliveryExceptionModal,
  "reschedule-booking-modal": RescheduleBookingModal,
  "seat-assignment-modal": SeatAssignmentModal,
  "enroll-learner-modal": EnrollLearnerModal,
  "grade-submission-modal": GradeSubmissionModal,
  "time-off-request-modal": TimeOffRequestModal,
  "compensation-change-modal": CompensationChangeModal,
  "rotate-api-key-modal": RotateApiKeyModal,
  "promote-deployment-modal": PromoteDeploymentModal,
  "model-evaluation-modal": ModelEvaluationModal,
  "prompt-test-modal": PromptTestModal,
  "device-command-modal": DeviceCommandModal,
  "firmware-update-modal": FirmwareUpdateModal,
  "property-inquiry-modal": PropertyInquiryModal,
  "lease-renewal-modal": LeaseRenewalModal,
  "moderate-content-modal": ModerateContentModal,
  "vendor-payout-modal": VendorPayoutModal,
  "trip-change-modal": TripChangeModal,
  "legal-hold-modal": LegalHoldModal,
  "publish-asset-modal": PublishAssetModal,
  "knowledge-merge-modal": KnowledgeMergeModal,
  "incident-response-wizard": IncidentResponseWizard,
  "vulnerability-remediation-wizard": VulnerabilityRemediationWizard,
  "ticket-escalation-wizard": TicketEscalationWizard,
  "customer-renewal-wizard": CustomerRenewalWizard,
  "payment-reconciliation-wizard": PaymentReconciliationWizard,
  "subscription-upgrade-wizard": SubscriptionUpgradeWizard,
  "stock-replenishment-wizard": StockReplenishmentWizard,
  "shipment-exception-wizard": ShipmentExceptionWizard,
  "reservation-setup-wizard": ReservationSetupWizard,
  "event-launch-wizard": EventLaunchWizard,
  "course-publishing-wizard": CoursePublishingWizard,
  "employee-onboarding-wizard": EmployeeOnboardingWizard,
  "candidate-hiring-wizard": CandidateHiringWizard,
  "api-product-launch-wizard": ApiProductLaunchWizard,
  "release-promotion-wizard": ReleasePromotionWizard,
  "model-deployment-wizard": ModelDeploymentWizard,
  "device-provisioning-wizard": DeviceProvisioningWizard,
  "property-listing-wizard": PropertyListingWizard,
  "vendor-onboarding-wizard": VendorOnboardingWizard,
  "legal-review-wizard": LegalReviewWizard,
  "media-publishing-wizard": MediaPublishingWizard,
  "knowledge-approval-wizard": KnowledgeApprovalWizard,
  "dynamic-schema-form": DynamicSchemaForm,
  "sectioned-settings-form": SectionedSettingsForm,
  "inline-quick-edit-form": InlineQuickEditForm,
  "query-builder-form": QueryBuilderForm,
  "filter-rule-builder": FilterRuleBuilder,
  "pricing-plan-form": PricingPlanForm,
  "checkout-address-form": CheckoutAddressForm,
  "support-reply-composer": SupportReplyComposer,
  "content-editor-form": ContentEditorForm,
  "metadata-editor-form": MetadataEditorForm,
  "api-request-builder-form": ApiRequestBuilderForm,
  "secret-reference-form": SecretReferenceForm,
  "schedule-rule-form": ScheduleRuleForm,
  "permission-policy-form": PermissionPolicyForm,
  "notification-preference-form": NotificationPreferenceForm,
  "survey-builder-form": SurveyBuilderForm,
  "localization-form": LocalizationForm,
  "theme-configurator-form": ThemeConfiguratorForm,
  "global-command-bar": GlobalCommandBar,
  "dense-admin-toolbar": DenseAdminToolbar,
  "product-hero-actions": ProductHeroActions,
  "resource-quick-actions": ResourceQuickActions,
  "bulk-selection-toolbar": BulkSelectionToolbar,
  "inline-row-actions": InlineRowActions,
  "split-button-actions": SplitButtonActions,
  "floating-create-action": FloatingCreateAction,
  "status-transition-bar": StatusTransitionBar,
  "review-decision-bar": ReviewDecisionBar,
  "pagination-toolbar": PaginationToolbar,
  "view-mode-switcher": ViewModeSwitcher,
  "filter-chip-bar": FilterChipBar,
  "saved-view-bar": SavedViewBar,
  "share-export-toolbar": ShareExportToolbar,
  "danger-zone-actions": DangerZoneActions,
  "icon-rail-navigation": IconRailNavigation,
  "collapsible-sidebar-navigation": CollapsibleSidebarNavigation,
  "workspace-top-navigation": WorkspaceTopNavigation,
  "breadcrumb-trail": BreadcrumbTrail,
  "tabbed-section-navigation": TabbedSectionNavigation,
  "stepper-navigation": StepperNavigation,
  "command-palette": CommandPalette,
  "mega-menu-navigation": MegaMenuNavigation,
  "context-switcher-navigation": ContextSwitcherNavigation,
  "mobile-bottom-navigation": MobileBottomNavigation,
  "admin-tree-navigation": AdminTreeNavigation,
  "docs-navigation": DocsNavigation,
  "calendar-navigation": CalendarNavigation,
  "product-subnav": ProductSubnav,
  "illustrated-empty-state": IllustratedEmptyState,
  "permission-denied-state": PermissionDeniedState,
  "offline-state": OfflineState,
  "maintenance-state": MaintenanceState,
  "rate-limit-state": RateLimitState,
  "data-load-error-state": DataLoadErrorState,
  "search-no-results-state": SearchNoResultsState,
  "import-success-state": ImportSuccessState,
  "operation-failed-state": OperationFailedState,
  "skeleton-dashboard-state": SkeletonDashboardState,
  "table-loading-state": TableLoadingState,
  "partial-data-warning": PartialDataWarning,
  "unsaved-changes-banner": UnsavedChangesBanner,
  "first-run-onboarding-state": FirstRunOnboardingState,
  "terminal-green-theme": TerminalGreenTheme,
  "glass-aurora-theme": GlassAuroraTheme,
  "editorial-paper-theme": EditorialPaperTheme,
  "brutalist-mono-theme": BrutalistMonoTheme,
  "enterprise-blue-theme": EnterpriseBlueTheme,
  "warm-commerce-theme": WarmCommerceTheme,
  "neon-cyber-theme": NeonCyberTheme,
  "soft-pastel-theme": SoftPastelTheme,
  "ocean-data-theme": OceanDataTheme,
  "sunset-operations-theme": SunsetOperationsTheme,
  "forest-saas-theme": ForestSaaSTheme,
  "monochrome-pro-theme": MonochromeProTheme,
  "lavender-creator-theme": LavenderCreatorTheme,
  "crimson-security-theme": CrimsonSecurityTheme,
  "sandstone-finance-theme": SandstoneFinanceTheme,
  "high-contrast-accessibility-theme": HighContrastAccessibilityTheme,
} as const;

export type GeneratedBlueprintPartId = keyof typeof BLUEPRINT_COMPONENTS;

export function blueprintComponent(componentId: string): ComponentType<Record<string, unknown>> | undefined {
  const component = BLUEPRINT_COMPONENTS[componentId as GeneratedBlueprintPartId];
  return component as unknown as ComponentType<Record<string, unknown>> | undefined;
}
