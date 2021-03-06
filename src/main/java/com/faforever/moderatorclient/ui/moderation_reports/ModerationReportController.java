package com.faforever.moderatorclient.ui.moderation_reports;

import com.faforever.commons.api.dto.ModerationReportStatus;
import com.faforever.moderatorclient.api.domain.ModerationReportService;
import com.faforever.moderatorclient.ui.*;
import com.faforever.moderatorclient.ui.domain.BanInfoFX;
import com.faforever.moderatorclient.ui.domain.ModerationReportFX;
import com.faforever.moderatorclient.ui.domain.PlayerFX;
import com.google.common.base.Strings;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ModerationReportController implements Controller<Region> {
	private final ModerationReportService moderationReportService;
	private final UiService uiService;
	private final PlatformService platformService;
	private final ObservableList<PlayerFX> reportedPlayersOfCurrentlySelectedReport = FXCollections.observableArrayList();

	public Region root;
	public ChoiceBox<ChooseableStatus> statusChoiceBox;
	public TextField playerNameFilterTextField;
	public TableView<ModerationReportFX> reportTableView;
	public Button editReportButton;
	public TableView<PlayerFX> reportedPlayerView;

	private FilteredList<ModerationReportFX> filteredItemList;
	private ObservableList<ModerationReportFX> itemList;
	private ModerationReportFX currentlySelectedItemNotNull;

	@Override
	public Region getRoot() {
		return root;
	}

	@FXML
	public void initialize() {
		statusChoiceBox.setItems(FXCollections.observableArrayList(ChooseableStatus.values()));
		statusChoiceBox.getSelectionModel().select(ChooseableStatus.ALL);

		editReportButton.disableProperty().bind(reportTableView.getSelectionModel().selectedItemProperty().isNull());

		itemList = FXCollections.observableArrayList();
		filteredItemList = new FilteredList<>(itemList);
		renewFilter();
		SortedList<ModerationReportFX> sortedItemList = new SortedList<>(filteredItemList);
		sortedItemList.comparatorProperty().bind(reportTableView.comparatorProperty());
		ViewHelper.buildModerationReportTableView(reportTableView, sortedItemList);
		statusChoiceBox.getSelectionModel().selectedItemProperty().addListener(observable -> renewFilter());
		playerNameFilterTextField.textProperty().addListener(observable -> renewFilter());

		reportTableView.getSelectionModel()
				.selectedItemProperty()
				.addListener((observable, oldValue, newValue) -> {
					if (newValue != null) {
						currentlySelectedItemNotNull = newValue;
						reportedPlayersOfCurrentlySelectedReport.setAll(newValue.getReportedUsers());
					}
				});

		ViewHelper.buildUserTableView(platformService, reportedPlayerView, reportedPlayersOfCurrentlySelectedReport, this::addBan);
	}

	private void addBan(PlayerFX accountFX) {
		BanInfoController banInfoController = uiService.loadFxml("ui/banInfo.fxml");
		BanInfoFX ban = new BanInfoFX();
		ban.setPlayer(accountFX);
		banInfoController.setBanInfo(ban);
		banInfoController.addPostedListener(banInfoFX -> onRefreshAllReports());
		Stage banInfoDialog = new Stage();
		banInfoDialog.setTitle("Apply new ban");
		banInfoDialog.setScene(new Scene(banInfoController.getRoot()));
		banInfoController.preSetReportId(currentlySelectedItemNotNull.getId());
		banInfoDialog.showAndWait();
	}

	private void renewFilter() {
		filteredItemList.setPredicate(moderationReportFx -> {
			String playerFilter = playerNameFilterTextField.getText().toLowerCase();
			if (!Strings.isNullOrEmpty(playerFilter)) {
				boolean reportedPlayerPositive = moderationReportFx.getReportedUsers().stream().anyMatch(accountFX -> accountFX.getLogin().toLowerCase().contains(playerFilter));
				boolean reporterPositive = moderationReportFx.getReporter().getLogin().toLowerCase().contains(playerFilter);
				if (!(reportedPlayerPositive || reporterPositive)) {
					return false;
				}
			}
			ChooseableStatus selectedItem = statusChoiceBox.getSelectionModel().getSelectedItem();
			if (selectedItem != null && selectedItem.getModerationReportStatus() != null) {
				ModerationReportStatus moderationReportStatus = selectedItem.getModerationReportStatus();
				return moderationReportFx.getReportStatus() == moderationReportStatus;
			}
			return true;
		});
	}


	public void onRefreshAllReports() {
		moderationReportService.getAllReports().thenAccept(reportFxes -> Platform.runLater(() -> itemList.setAll(reportFxes))).exceptionally(throwable -> {
			log.error("error loading reports", throwable);
			return null;
		});
	}

	public void onEdit() {
		EditModerationReportController editModerationReportController = uiService.loadFxml("ui/edit_moderation_report.fxml");
		editModerationReportController.setModerationReportFx(reportTableView.getSelectionModel().getSelectedItem());
		editModerationReportController.setOnSaveRunnable(() -> Platform.runLater(this::onRefreshAllReports));

		Stage newCategoryDialog = new Stage();
		newCategoryDialog.setTitle("Edit Report");
		newCategoryDialog.setScene(new Scene(editModerationReportController.getRoot()));
		newCategoryDialog.showAndWait();
	}

	private enum ChooseableStatus {
		ALL(null),
		AWAITING(ModerationReportStatus.AWAITING),
		PROCESSING(ModerationReportStatus.PROCESSING),
		COMPLETED(ModerationReportStatus.COMPLETED),
		DISCARDED(ModerationReportStatus.DISCARDED);

		@Getter
		private final ModerationReportStatus moderationReportStatus;

		ChooseableStatus(ModerationReportStatus moderationReportStatus) {
			this.moderationReportStatus = moderationReportStatus;
		}
	}
}
