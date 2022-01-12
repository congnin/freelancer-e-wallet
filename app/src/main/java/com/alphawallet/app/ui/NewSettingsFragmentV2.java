package com.alphawallet.app.ui;


import static android.app.Activity.RESULT_OK;
import static com.alphawallet.app.C.CHANGE_CURRENCY;
import static com.alphawallet.app.C.Key.WALLET;
import static com.alphawallet.app.C.RESET_WALLET;
import static com.alphawallet.app.entity.BackupOperationType.BACKUP_HD_KEY;
import static com.alphawallet.app.entity.BackupOperationType.BACKUP_KEYSTORE_KEY;
import static com.alphawallet.app.ui.HomeActivity.RESET_TOKEN_SERVICE;
import static com.alphawallet.token.tools.TokenDefinition.TOKENSCRIPT_CURRENT_SCHEMA;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.alphawallet.app.BuildConfig;
import com.alphawallet.app.C;
import com.alphawallet.app.R;
import com.alphawallet.app.entity.BackupOperationType;
import com.alphawallet.app.entity.CustomViewSettings;
import com.alphawallet.app.entity.Wallet;
import com.alphawallet.app.entity.WalletPage;
import com.alphawallet.app.entity.WalletType;
import com.alphawallet.app.interact.GenericWalletInteract;
import com.alphawallet.app.util.LocaleUtils;
import com.alphawallet.app.util.UpdateUtils;
import com.alphawallet.app.viewmodel.NewSettingsViewModel;
import com.alphawallet.app.viewmodel.NewSettingsViewModelFactory;
import com.alphawallet.app.widget.NotificationView;
import com.alphawallet.app.widget.SettingsItemView;

import java.util.Locale;

import javax.inject.Inject;

import dagger.android.support.AndroidSupportInjection;

public class NewSettingsFragmentV2 extends BaseFragment {
    @Inject
    NewSettingsViewModelFactory newSettingsViewModelFactory;

    private NewSettingsViewModel viewModel;

    private LinearLayout walletSettingsLayout;

    private SettingsItemView myAddressSetting;
    private SettingsItemView changeWalletSetting;
    private SettingsItemView backUpWalletSetting;

    private LinearLayout layoutBackup;
    private View warningSeparator;
    private Button backupButton;
    private TextView backupTitle;
    private TextView backupDetail;
    private ImageView backupMenuButton;
    private int pendingUpdate = 0;
    private LinearLayout updateLayout;

    private Wallet wallet;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        AndroidSupportInjection.inject(this);
        viewModel = new ViewModelProvider(this, newSettingsViewModelFactory)
                .get(NewSettingsViewModel.class);
        viewModel.defaultWallet().observe(getViewLifecycleOwner(), this::onDefaultWallet);
        viewModel.backUpMessage().observe(getViewLifecycleOwner(), this::backupWarning);
        LocaleUtils.setActiveLocale(getContext());

        View view = inflater.inflate(R.layout.fragment_settings_v2, container, false);

        toolbar(view);

        setToolbarTitle(R.string.toolbar_header_settings);

        initializeSettings(view);

        addSettingsToLayout();

        setInitialSettingsData(view);

        initBackupWarningViews(view);

        checkPendingUpdate(view);

        return view;
    }

    public void signalUpdate(int updateVersion)
    {
        //add wallet update signal to adapter
        pendingUpdate = updateVersion;
        checkPendingUpdate(getView());
    }

    private void initBackupWarningViews(View view) {
        layoutBackup = view.findViewById(R.id.layout_item_warning);
        backupTitle = view.findViewById(R.id.text_title);
        backupDetail = view.findViewById(R.id.text_detail);
        backupButton = view.findViewById(R.id.button_backup);
        backupMenuButton = view.findViewById(R.id.btn_menu);
        layoutBackup.setVisibility(View.GONE);
        warningSeparator = view.findViewById(R.id.warning_separator);
    }

    private void initializeSettings(View view) {
        walletSettingsLayout = view.findViewById(R.id.layout_settings_wallet);
        updateLayout = view.findViewById(R.id.layout_update);

        myAddressSetting =
                new SettingsItemView.Builder(getContext())
                        .withIcon(R.drawable.ic_settings_wallet_address2)
                        .withTitle(R.string.title_show_wallet_address)
                        .withListener(this::onShowWalletAddressSettingClicked)
                        .build();

        changeWalletSetting =
                new SettingsItemView.Builder(getContext())
                        .withIcon(R.drawable.ic_settings_change_wallet2)
                        .withTitle(R.string.title_change_add_wallet)
                        .withListener(this::onChangeWalletSettingClicked)
                        .build();

        backUpWalletSetting =
                new SettingsItemView.Builder(getContext())
                        .withIcon(R.drawable.ic_settings_backup2)
                        .withTitle(R.string.title_back_up_wallet)
                        .withListener(this::onBackUpWalletSettingClicked)
                        .build();
    }

    private void addSettingsToLayout() {
        int walletIndex = 0;
        int systemIndex = 0;
        int supportIndex = 0;

        walletSettingsLayout.addView(myAddressSetting, walletIndex++);

        if (CustomViewSettings.canChangeWallets())
            walletSettingsLayout.addView(changeWalletSetting, walletIndex++);

        walletSettingsLayout.addView(backUpWalletSetting, walletIndex++);
    }

    private void setInitialSettingsData(View view) {
        TextView appVersionText = view.findViewById(R.id.text_version);
        appVersionText.setText(String.format(Locale.getDefault(), "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        TextView tokenScriptVersionText = view.findViewById(R.id.text_tokenscript_compatibility);
        tokenScriptVersionText.setText(TOKENSCRIPT_CURRENT_SCHEMA);
    }

    private void openShowSeedPhrase(Wallet wallet)
    {
        if (wallet.type != WalletType.HDKEY) return;

        Intent intent = new Intent(getContext(), BackupKeyActivity.class);
        intent.putExtra(WALLET, wallet);
        intent.putExtra("TYPE", BackupOperationType.SHOW_SEED_PHRASE_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        startActivity(intent);
    }

    ActivityResultLauncher<Intent> handleBackupClick = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                String keyBackup = null;
                boolean noLockScreen = false;
                Intent data = result.getData();
                if (data != null) keyBackup = data.getStringExtra("Key");
                if (data != null) noLockScreen = data.getBooleanExtra("nolock", false);
                if (result.getResultCode() == RESULT_OK)
                {
                    ((HomeActivity)getActivity()).backupWalletSuccess(keyBackup);
                }
                else
                {
                    ((HomeActivity)getActivity()).backupWalletFail(keyBackup, noLockScreen);
                }
            });

    private void openBackupActivity(Wallet wallet) {
        Intent intent = new Intent(getContext(), BackupFlowActivity.class);
        intent.putExtra(WALLET, wallet);

        switch (wallet.type)
        {
            case HDKEY:
                intent.putExtra("TYPE", BACKUP_HD_KEY);
                break;
            case KEYSTORE_LEGACY:
            case KEYSTORE:
                intent.putExtra("TYPE", BACKUP_KEYSTORE_KEY);
                break;
        }

        //override if this is an upgrade
        switch (wallet.authLevel)
        {
            case NOT_SET:
            case STRONGBOX_NO_AUTHENTICATION:
            case TEE_NO_AUTHENTICATION:
                if (wallet.lastBackupTime > 0)
                {
                    intent.putExtra("TYPE", BackupOperationType.UPGRADE_KEY);
                }
                break;
            default:
                break;
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        handleBackupClick.launch(intent);
    }

    private void onDefaultWallet(Wallet wallet) {
        this.wallet = wallet;
        if (wallet.address != null)
        {
            if (!wallet.ENSname.isEmpty())
            {
                changeWalletSetting.setSubtitle(wallet.ENSname + " | " + wallet.address);
            }
            else
            {
                changeWalletSetting.setSubtitle(wallet.address);
            }
        }

        switch (wallet.authLevel) {
            case NOT_SET:
            case STRONGBOX_NO_AUTHENTICATION:
            case TEE_NO_AUTHENTICATION:
                if (wallet.lastBackupTime > 0) {
                    backUpWalletSetting.setTitle(getString(R.string.action_upgrade_key));
                    backUpWalletSetting.setSubtitle(getString(R.string.not_locked));
                } else {
                    backUpWalletSetting.setTitle(getString(R.string.back_up_this_wallet));
//                    backUpWalletSetting.setSubtitle(getString(R.string.back_up_now));
                }
                break;
            case TEE_AUTHENTICATION:
            case STRONGBOX_AUTHENTICATION:
                backUpWalletSetting.setTitle(getString(R.string.back_up_this_wallet));
                backUpWalletSetting.setSubtitle(getString(R.string.key_secure));
                break;
        }

        switch (wallet.type)
        {
            case NOT_DEFINED:
                break;
            case KEYSTORE:
                break;
            case WATCH:
                backUpWalletSetting.setVisibility(View.GONE);
                break;
            case TEXT_MARKER:
                break;
            case KEYSTORE_LEGACY:
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel == null)
        {
            ((HomeActivity)getActivity()).resetFragment(WalletPage.SETTINGS);
        }
        else
        {
            viewModel.prepare();
        }
    }

    public void backupSeedSuccess(boolean hasNoLock) {
        if (viewModel != null) viewModel.TestWalletBackup();
        if (layoutBackup != null) layoutBackup.setVisibility(View.GONE);
        if (hasNoLock)
        {
            backUpWalletSetting.setSubtitle(getString(R.string.not_locked));
        }
    }

    private void backupWarning(String s) {
        if (s.equals(viewModel.defaultWallet().getValue().address)) {
            addBackupNotice(GenericWalletInteract.BackupLevel.WALLET_HAS_HIGH_VALUE);
        } else {
            if (layoutBackup != null) {
                layoutBackup.setVisibility(View.GONE);
            }
            //remove the number prompt
            if (getActivity() != null)
                ((HomeActivity) getActivity()).removeSettingsBadgeKey(C.KEY_NEEDS_BACKUP);
            onDefaultWallet(viewModel.defaultWallet().getValue());
        }
    }

    void addBackupNotice(GenericWalletInteract.BackupLevel walletValue) {
        layoutBackup.setVisibility(View.GONE);
        warningSeparator.setVisibility(View.GONE);
        if (wallet != null) {
            backupButton.setText(getString(R.string.back_up_wallet_action, wallet.address.substring(0, 5)));
            backupButton.setOnClickListener(v -> openBackupActivity(wallet));
            backupTitle.setText(getString(R.string.wallet_not_backed_up));
            layoutBackup.setBackgroundResource(R.drawable.background_warning_red_8dp);
            backupDetail.setText(getString(R.string.backup_wallet_detail));

            if (getActivity() != null) {
                ((HomeActivity) getActivity()).addSettingsBadgeKey(C.KEY_NEEDS_BACKUP);
            }
        }
    }

    private void showPopup(View view, String walletAddress) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View popupView = inflater.inflate(R.layout.popup_remind_later, null);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, true);
        popupView.setOnClickListener(v -> {
            viewModel.setIsDismissed(walletAddress, true);
            backedUp(walletAddress);
            popupWindow.dismiss();
        });
        popupWindow.showAsDropDown(view, 0, 0);
    }

    private void backedUp(String walletAddress) {
        layoutBackup.setVisibility(View.GONE);
        warningSeparator.setVisibility(View.GONE);
        if (getActivity() != null)
            ((HomeActivity) getActivity()).postponeWalletBackupWarning(walletAddress);
    }

    private void onShowWalletAddressSettingClicked() {
        viewModel.showMyAddress(getContext());
    }

    private void onChangeWalletSettingClicked() {
        viewModel.showManageWallets(getContext(), false);
    }

    private void onBackUpWalletSettingClicked() {
        Wallet wallet = viewModel.defaultWallet().getValue();
        if (wallet != null) {
            openBackupActivity(wallet);
        }
    }

    private void onShowSeedPhrase()
    {
        Wallet wallet = viewModel.defaultWallet().getValue();
        if (wallet != null) {
            openShowSeedPhrase(wallet);
        }
    }

    private void onNameThisWallet()
    {
        Intent intent = new Intent(getActivity(), NameThisWalletActivity.class);
        requireActivity().startActivity(intent);
    }

    private void onBiometricsSettingClicked() {
        // TODO: Implementation
    }

    ActivityResultLauncher<Intent> networkSettingsHandler = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                //send instruction to restart tokenService
                getParentFragmentManager().setFragmentResult(RESET_TOKEN_SERVICE, new Bundle());
            });

    private void onSelectNetworksSettingClicked() {
        Intent intent = new Intent(getActivity(), SelectNetworkFilterActivity.class);
        intent.putExtra(C.EXTRA_SINGLE_ITEM, false);
        networkSettingsHandler.launch(intent);
    }

    ActivityResultLauncher<Intent> advancedSettingsHandler = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                if (data == null) return;
                if (data.getBooleanExtra(RESET_WALLET, false))
                {
                    getParentFragmentManager().setFragmentResult(RESET_WALLET, new Bundle());
                }
                else if (data.getBooleanExtra(CHANGE_CURRENCY, false))
                {
                    getParentFragmentManager().setFragmentResult(CHANGE_CURRENCY, new Bundle());
                }
            });

    private void onAdvancedSettingClicked() {
        Intent intent = new Intent(getActivity(), AdvancedSettingsActivity.class);
        advancedSettingsHandler.launch(intent);
    }

    private void onSupportSettingClicked() {
        Intent intent = new Intent(getActivity(), SupportSettingsActivity.class);
        startActivity(intent);
    }

    private void onWalletConnectSettingClicked() {
        Intent intent = new Intent(getActivity(), WalletConnectSessionActivity.class);
        intent.putExtra("wallet", wallet);
        startActivity(intent);
    }

    private void checkPendingUpdate(View view)
    {
        if (updateLayout == null) return;

        if (pendingUpdate > 0)
        {
            final int thisPendingUpdate = pendingUpdate; //avoid OS reclaiming the value
            updateLayout.setVisibility(View.VISIBLE);
            TextView current = view.findViewById(R.id.text_detail_current);
            TextView available = view.findViewById(R.id.text_detail_available);
            current.setText(getString(R.string.installed_version, String.valueOf(BuildConfig.VERSION_CODE)));
            available.setText(getString(R.string.available_version, String.valueOf(pendingUpdate)));
            if (getActivity() != null) {
                ((HomeActivity) getActivity()).addSettingsBadgeKey(C.KEY_UPDATE_AVAILABLE);
            }

            updateLayout.setOnClickListener(v -> {
                UpdateUtils.pushUpdateDialog(getActivity());
                updateLayout.setVisibility(View.GONE);
                pendingUpdate = 0;
                if (getActivity() != null) {
                    ((HomeActivity) getActivity()).removeSettingsBadgeKey(C.KEY_UPDATE_AVAILABLE);
                }
            });
        }
        else
        {
            updateLayout.setVisibility(View.GONE);
        }
    }
}
