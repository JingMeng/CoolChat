package com.cooloongwu.coolchat.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.apkfuns.logutils.LogUtils;
import com.cooloongwu.coolchat.R;
import com.cooloongwu.coolchat.adapter.ChatAdapter;
import com.cooloongwu.coolchat.base.AppConfig;
import com.cooloongwu.coolchat.base.BaseActivity;
import com.cooloongwu.coolchat.entity.Chat;
import com.cooloongwu.coolchat.entity.Conversation;
import com.cooloongwu.coolchat.entity.Group;
import com.cooloongwu.coolchat.fragment.ChatMoreFragment;
import com.cooloongwu.coolchat.fragment.EmojiFragment;
import com.cooloongwu.coolchat.fragment.RecordFragment;
import com.cooloongwu.coolchat.utils.DisplayUtils;
import com.cooloongwu.coolchat.utils.GreenDAOUtils;
import com.cooloongwu.coolchat.utils.KeyboardUtils;
import com.cooloongwu.coolchat.utils.QupaiUploadUtils;
import com.cooloongwu.coolchat.utils.SendMessageUtils;
import com.cooloongwu.coolchat.utils.ToastUtils;
import com.cooloongwu.emoji.entity.Emoji;
import com.cooloongwu.emoji.utils.EmojiTextUtils;
import com.cooloongwu.greendao.gen.ChatDao;
import com.cooloongwu.greendao.gen.ConversationDao;
import com.cooloongwu.greendao.gen.GroupDao;
import com.cooloongwu.qupai.RecordResult;
import com.duanqu.qupai.sdk.android.QupaiManager;
import com.duanqu.qupai.sdk.android.QupaiService;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.nereo.multi_image_selector.MultiImageSelector;
import me.nereo.multi_image_selector.MultiImageSelectorActivity;

public class ChatActivity extends BaseActivity implements View.OnClickListener, EmojiFragment.OnEmojiClickListener, CompoundButton.OnCheckedChangeListener {

    private ImageButton imgbtn_send;
    private EditText edit_input;

    private CheckBox checkbox_audio;
    private CheckBox checkbox_emoji;
    private CheckBox checkbox_gallery;
    private CheckBox checkbox_video;
    private CheckBox checkbox_more;
    private int checkedId = -1;

    private static TextView text_unread_msg;
    private LinearLayout layout_multi;

    private ArrayList<Chat> chatListData = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private static RecyclerView recyclerView;
    private static ChatAdapter adapter;
    private MyHandler handler = new MyHandler(ChatActivity.this);

    private Toolbar toolbar;
    private int chatId;
    private String chatType;
    private String chatName;

    //在当前页面接收到另一个好友或者群组的聊天消息
    private int otherChatId;
    private String otherChatType;
    private String otherChatName;

    private final int REQUEST_IMAGE = 0x01;
    private final int REQUEST_VIDEO = 0x02;

    private long latestId = 0;

    private EmojiFragment emojiFragment;
    private ChatMoreFragment chatMoreFragment;
    private RecordFragment recordFragment;

    private boolean isKeyboardShowing = false;
    private boolean isMultiLayoutShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
//        全屏状态下，adjustResize和adjustPan没有作用，点击输入框都会将页面挤上去（在别的项目中研究了半天，日了狗了。。。。。）
//        还有一点就是当GLSurfaceView上有控件并且被挤出去后就无法在重新显示！！！！！
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
//                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        EventBus.getDefault().register(this);
        getData();
        initViews();

        isSetBoardHeight();
        initRecentChatData(chatType, chatId);
    }

    private void isSetBoardHeight() {
        if (AppConfig.getKeyboardHeight(ChatActivity.this) == 0) {
            DisplayUtils.detectKeyboardHeight(ChatActivity.this);
        }
    }

    private void getData() {
        Intent intent = getIntent();
        chatId = intent.getIntExtra("chatId", 0);                       //好友或者群组的ID
        chatType = intent.getStringExtra("chatType");                   //群组还是好友
        chatName = intent.getStringExtra("chatName");                   //群组名或者好友名
        LogUtils.e("聊天信息" + "当前在跟" + chatType + "：ID为" + chatId + "的" + chatName + "聊天");
        initToolbar(chatName);

        //保存当前聊天对象的信息
        AppConfig.setUserCurrentChatId(ChatActivity.this, chatId);
        AppConfig.setUserCurrentChatType(ChatActivity.this, chatType);
    }

    private void initToolbar(String title) {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(title);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

    }

    private void initViews() {
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(ChatActivity.this);
        //layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ChatAdapter(ChatActivity.this, chatListData);
        recyclerView.setAdapter(adapter);
        edit_input = (EditText) findViewById(R.id.edit_input);
        edit_input.addTextChangedListener(textWatcher);
        edit_input.setOnClickListener(this);
        text_unread_msg = (TextView) findViewById(R.id.text_unread_msg);

        layout_multi = (LinearLayout) findViewById(R.id.layout_multi);
        imgbtn_send = (ImageButton) findViewById(R.id.imgbtn_send);
        imgbtn_send.setClickable(false);

        checkbox_audio = (CheckBox) findViewById(R.id.checkbox_audio);
        checkbox_emoji = (CheckBox) findViewById(R.id.checkbox_emoji);
        checkbox_gallery = (CheckBox) findViewById(R.id.checkbox_gallery);
        checkbox_video = (CheckBox) findViewById(R.id.checkbox_video);
        checkbox_more = (CheckBox) findViewById(R.id.checkbox_more);

        checkbox_audio.setOnCheckedChangeListener(this);
        checkbox_emoji.setOnCheckedChangeListener(this);
        checkbox_video.setOnCheckedChangeListener(this);
        checkbox_gallery.setOnCheckedChangeListener(this);
        checkbox_more.setOnCheckedChangeListener(this);

        text_unread_msg.setOnClickListener(this);
        imgbtn_send.setOnClickListener(this);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                initMoreChatData(chatType, chatId);
            }
        });
    }

    /**
     * 加载最近的聊天消息，默认5条（QQ是15条）
     */
    private void initRecentChatData(String chatType, int chatId) {
        ChatDao chatDao = GreenDAOUtils.getInstance(ChatActivity.this).getChatDao();
        List<Chat> chats;

        //从Id最大的往小查
        if ("friend".equals(chatType)) {
            chats = chatDao.queryBuilder()
                    .where(ChatDao.Properties.ChatType.eq(chatType))
                    .whereOr(ChatDao.Properties.FromId.eq(chatId), ChatDao.Properties.ToId.eq(chatId))
                    .limit(5)
                    .orderDesc(ChatDao.Properties.Time)
                    .build()
                    .list();
        } else {
            chats = chatDao.queryBuilder()
                    .where(ChatDao.Properties.ChatType.eq(chatType), ChatDao.Properties.ToId.eq(chatId))
                    .limit(5)
                    .orderDesc(ChatDao.Properties.Time)
                    .build()
                    .list();
        }

        if (!chats.isEmpty()) {
            latestId = chats.get(chats.size() - 1).getId();
            LogUtils.e("数据的索引" + latestId);
            //倒序排列下
            Collections.reverse(chats);
            //为了加载和其他人聊天信息的时候清空屏幕
            chatListData.clear();
            chatListData.addAll(chats);

            adapter.notifyDataSetChanged();
            int itemCount = adapter.getItemCount() - 1;
            if (itemCount > 0) {
                recyclerView.smoothScrollToPosition(itemCount);
            }
        }
    }

    /**
     * 下拉加载更多聊天信息
     */
    private void initMoreChatData(String chatType, int chatId) {
        ChatDao chatDao = GreenDAOUtils.getInstance(ChatActivity.this).getChatDao();
        List<Chat> chats;
        //从Id最大的往小查
        if ("friend".equals(chatType)) {
            chats = chatDao.queryBuilder()
                    .where(ChatDao.Properties.ChatType.eq(chatType), ChatDao.Properties.Id.lt(latestId))
                    .whereOr(ChatDao.Properties.FromId.eq(chatId), ChatDao.Properties.ToId.eq(chatId))
                    .limit(10)
                    .orderDesc(ChatDao.Properties.Time)
                    .build()
                    .list();
        } else {
            chats = chatDao.queryBuilder()
                    .where(ChatDao.Properties.ChatType.eq(chatType), ChatDao.Properties.Id.lt(latestId), ChatDao.Properties.ToId.eq(chatId))
                    .limit(10)
                    .orderDesc(ChatDao.Properties.Time)
                    .build()
                    .list();
        }
        if (!chats.isEmpty()) {
            latestId = chats.get(chats.size() - 1).getId();
            LogUtils.e("数据的索引" + latestId);
            Collections.reverse(chats);
            chatListData.addAll(0, chats);
            adapter.notifyDataSetChanged();
        } else {
            ToastUtils.showShort(ChatActivity.this, "没有更多数据了");
        }
        swipeRefreshLayout.setRefreshing(false);
    }


    @Subscribe
    public void onEventMainThread(JSONObject jsonObject) {
        try {

            /**
             * 如果toWhich是friend，那么toId可能是自己的ID（朋友发的消息）或者朋友的Id（自己发的消息）
             *                    那么fromId可能是自己的ID（自己发的消息）或者朋友的ID（朋友发的消息）
             *
             * 如果toWhich是group，那么toId就是群组的Id
             *                   那么fromId可能是自己的ID（自己发的消息）或者群组中其他人的ID（群组中其他人发的消息）
             */
            String toWhich = jsonObject.getString("toWhich");   //可能是friend或者group
            int toId = jsonObject.getInt("toId");               //可能是我自己的ID或者对方的ID
            int fromId = jsonObject.getInt("fromId");           //可能是我自己的ID或者对方的ID

            String fromAvatar = jsonObject.getString("fromAvatar");
            String fromName = jsonObject.getString("fromName");
            String content = jsonObject.getString("content");
            String contentType = jsonObject.getString("contentType");
            String time = jsonObject.getString("time");
            int msgId = jsonObject.getInt("msgId");

            if (chatType.equals(toWhich)) {
                //跟当前聊天类型匹配，是群组或者好友的消息
                if ("friend".equals(toWhich)) {
                    //当前在跟好友聊天，需判断是不是当前好友的消息
                    if ((toId == chatId && fromId == AppConfig.getUserId(ChatActivity.this)) //我发给当前朋友的消息
                            || (toId == AppConfig.getUserId(ChatActivity.this) && fromId == chatId)//朋友发给我的消息
                            ) {
                        //是跟当前好友的聊天消息
                        List<Chat> chats = new ArrayList<>();
                        Chat chat = new Chat();
                        chat.setFromId(fromId);
                        chat.setFromAvatar(fromAvatar);
                        chat.setFromName(fromName);
                        chat.setContent(content);
                        chat.setContentType(contentType);
                        chat.setToId(toId);
                        chat.setTime(time);
                        chat.setIsRead(true);             //消息已读
                        chat.setMsgId(msgId);           //消息已读
                        if ("audio".equals(contentType)) {
                            chat.setAudioLength(jsonObject.getString("audioLength"));
                        }

                        if ("delete".equals(contentType)) {
                            for (int i = 0; i < chatListData.size(); i++) {
                                if (chatListData.get(i).getMsgId() == Integer.parseInt(content)) {
                                    chatListData.get(i).setContentType("delete");
                                    adapter.notifyDataSetChanged();
                                }
                            }
                        } else {
                            chats.add(chat);
                            chatListData.addAll(chats);

                            Message msg = new Message();
                            msg.what = 0;
                            handler.sendMessage(msg);
                        }

                    } else {
                        //是好友信息，但不是当前聊天好友的
                        showOtherFriendMsg(fromName + "：" + content, toWhich, fromId, fromName);
                    }
                } else {
                    //当前在跟群组聊天，需判断是不是当前群组的消息
                    if (chatId == toId) {
                        List<Chat> chats = new ArrayList<>();
                        Chat chat = new Chat();
                        chat.setContent(content);
                        chat.setContentType(contentType);
                        chat.setFromAvatar(fromAvatar);
                        chat.setFromId(fromId);
                        chat.setTime(time);
                        chat.setToId(toId);
                        chat.setIsRead(true);
                        if ("audio".equals(contentType)) {
                            chat.setAudioLength(jsonObject.getString("audioLength"));
                        }
                        chats.add(chat);
                        chatListData.addAll(chats);

                        Message msg = new Message();
                        msg.what = 0;
                        handler.sendMessage(msg);
                    } else {
                        //不是当前群组的聊天消息，提示来消息了即可
                        showOtherGroupMsg(fromName + "：" + content, toWhich, toId);
                    }
                }
            } else {
                //跟当前聊天类型不匹配，比如：当前在跟好友聊天，来的是群组消息；当前跟群组聊天，来的是好友消息
                if ("friend".equals(chatType)) {
                    showOtherFriendMsg(fromName + "：" + content, toWhich, fromId, fromName);
                } else {
                    showOtherGroupMsg(fromName + "：" + content, toWhich, toId);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 提醒其他好友或者群组来消息了
     *
     * @param str 消息内容
     */
    private void showOtherFriendMsg(String str, String chatType, int chatId, String chatName) {
        otherChatType = chatType;
        otherChatId = chatId;
        otherChatName = chatName;

        Message msg = new Message();
        Bundle bundle = new Bundle();
        bundle.putString("otherMsg", str);
        msg.setData(bundle);
        msg.what = 1;
        handler.sendMessage(msg);
        handler.sendEmptyMessageDelayed(2, 5000);
    }

    /**
     * 提醒其他好友或者群组来消息了
     *
     * @param str 消息内容
     */
    private void showOtherGroupMsg(String str, String chatType, int chatId) {
        otherChatType = chatType;
        otherChatId = chatId;

        GroupDao groupDao = GreenDAOUtils.getInstance(ChatActivity.this).getGroupDao();
        Group group = groupDao.queryBuilder()
                .where(GroupDao.Properties.GroupId.eq(chatId))
                .build().unique();
        otherChatName = group.getGroupName();

        Message msg = new Message();
        Bundle bundle = new Bundle();
        bundle.putString("otherMsg", otherChatName + "：" + str);
        msg.setData(bundle);
        msg.what = 1;
        handler.sendMessage(msg);
        handler.sendEmptyMessageDelayed(2, 5000);
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.edit_input:
                layout_multi.postDelayed(hideMultiLayoutRunnable, 500);
                isKeyboardShowing = true;
                break;
            case R.id.imgbtn_send:
                SendMessageUtils.sendTextMessage(ChatActivity.this, edit_input.getText().toString().trim());
                edit_input.setText("");
                imgbtn_send.setClickable(false);
                break;

            case R.id.text_unread_msg:
                text_unread_msg.setVisibility(View.GONE);
                //刷新当前页面，加载与点击的好友或者群组聊天
                chatId = otherChatId;
                chatType = otherChatType;
                chatName = otherChatName;

                toolbar.setTitle(chatName);
                initRecentChatData(chatType, chatId);

                //并将聊天列表页的未读消息数置为0
                ConversationDao conversationDao = GreenDAOUtils.getInstance(ChatActivity.this).getConversationDao();
                Conversation conversation = conversationDao.queryBuilder()
                        .where(ConversationDao.Properties.Type.eq(chatType), ConversationDao.Properties.MultiId.eq(chatId))
                        .build().unique();
                conversation.setUnReadNum(0);
                conversationDao.update(conversation);

                //通知聊天列表页更新
                EventBus.getDefault().post(new Conversation());
                //重新指定当前聊天的对象
                AppConfig.setUserCurrentChatId(ChatActivity.this, chatId);
                AppConfig.setUserCurrentChatType(ChatActivity.this, chatType);
                break;

            default:
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            checkedId = buttonView.getId();
        } else {
            hideMultiLayout();
            return;
        }
        //把其他checkbox的选中状态取消
        checkbox_audio.setChecked(checkbox_audio.getId() == checkedId);
        checkbox_emoji.setChecked(checkbox_emoji.getId() == checkedId);
        checkbox_gallery.setChecked(false);
        checkbox_video.setChecked(false);
        checkbox_more.setChecked(checkbox_more.getId() == checkedId);

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        switch (buttonView.getId()) {
            case R.id.checkbox_audio:
                showMultiLayout();
                if (recordFragment == null) {
                    recordFragment = new RecordFragment();
                }
                fragmentTransaction.replace(R.id.layout_multi, recordFragment);
                fragmentTransaction.commit();
                isMultiLayoutShowing = true;
                break;
            case R.id.checkbox_emoji:
                showMultiLayout();
                if (emojiFragment == null) {
                    emojiFragment = new EmojiFragment();
                }
                fragmentTransaction.replace(R.id.layout_multi, emojiFragment);
                fragmentTransaction.commit();
                isMultiLayoutShowing = true;
                break;
            case R.id.checkbox_gallery:
                hideMultiLayout();
                openImageGallery();
                break;
            case R.id.checkbox_video:
                hideMultiLayout();
                openRecordPage();
                break;
            case R.id.checkbox_more:
                showMultiLayout();
                if (chatMoreFragment == null) {
                    chatMoreFragment = new ChatMoreFragment();
                }
                fragmentTransaction.replace(R.id.layout_multi, chatMoreFragment);
                fragmentTransaction.commit();
                isMultiLayoutShowing = true;
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chat, menu);
        if ("friend".equals(chatType)) {
            menu.getItem(0).setIcon(R.mipmap.icon_menu_profile_user);
        } else {
            menu.getItem(0).setIcon(R.mipmap.icon_menu_profile_group);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = new Intent();
        if ("friend".equals(chatType)) {
            //跳转到好友个人资料页面
            intent.setClass(ChatActivity.this, UserProfileActivity.class);
        } else {
            //跳转到群组资料页面
            intent.setClass(ChatActivity.this, GroupProfileActivity.class);
        }
        intent.putExtra("id", chatId);
        startActivity(intent);
        return super.onOptionsItemSelected(item);
    }

    /**
     * 处理图片的发送
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == RESULT_OK) {
                List<String> paths = data.getStringArrayListExtra(MultiImageSelectorActivity.EXTRA_RESULT);
                for (String path : paths) {
                    SendMessageUtils.sendImageMessage(ChatActivity.this, new File(path));
                }
            }
        }

        if (requestCode == REQUEST_VIDEO) {
            if (resultCode == RESULT_OK) {
                RecordResult result = new RecordResult(data);
                //得到视频地址，和缩略图地址的数组，返回十张缩略图
                String videoPath = result.getPath();
                String thumbnails[] = result.getThumbnail();
                result.getDuration();

                QupaiUploadUtils.startUpload(ChatActivity.this, videoPath, thumbnails[0]);

                /**
                 * 清除草稿,草稿文件将会删除。所以在这之前我们执行拷贝move操作。
                 * 上面的拷贝操作请自行实现，第一版本的copyVideoFile接口不再使用
                 */
//            QupaiService qupaiService = QupaiManager
//                    .getQupaiService(MainActivity.this);
//            qupaiService.deleteDraft(getApplicationContext(),data);

            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        handler.removeCallbacksAndMessages(null);

        //设置当前聊天对象，表示没有
        AppConfig.setUserCurrentChatId(ChatActivity.this, 0);
        AppConfig.setUserCurrentChatType(ChatActivity.this, "");
    }

    @Override
    public void onEmojiDelete() {
        String text = edit_input.getText().toString();
        if (text.isEmpty()) {
            return;
        }
        if ("]".equals(text.substring(text.length() - 1, text.length()))) {
            int index = text.lastIndexOf("[");
            if (index == -1) {
                int action = KeyEvent.ACTION_DOWN;
                int code = KeyEvent.KEYCODE_DEL;
                KeyEvent event = new KeyEvent(action, code);
                edit_input.onKeyDown(KeyEvent.KEYCODE_DEL, event);
                displayEditTextView();
                return;
            }
            edit_input.getText().delete(index, text.length());
            displayEditTextView();
            return;
        }
        int action = KeyEvent.ACTION_DOWN;
        int code = KeyEvent.KEYCODE_DEL;
        KeyEvent event = new KeyEvent(action, code);
        edit_input.onKeyDown(KeyEvent.KEYCODE_DEL, event);
    }

    @Override
    public void onEmojiClick(Emoji emoji) {
        if (emoji != null) {
            int index = edit_input.getSelectionStart();
            Editable editable = edit_input.getEditableText();
            if (index < 0) {
                editable.append(emoji.getContent());
            } else {
                editable.insert(index, emoji.getContent());
            }
        }
        displayEditTextView();
    }

    private void displayEditTextView() {
        try {
            edit_input.setText(EmojiTextUtils.getEditTextContent(edit_input.getText().toString().trim(), ChatActivity.this, edit_input));
            edit_input.setSelection(edit_input.getText().length());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        LogUtils.e("dispatchTouchEvent" + "，当前点击高度" + ev.getY());
        LogUtils.e("dispatchTouchEvent" + "，当前无效区域高度" + (DisplayUtils.getScreenHeight(this) - DisplayUtils.dp2px(this, 120)));
        if (ev.getY() < DisplayUtils.getScreenHeight(this) - AppConfig.getKeyboardHeight(this) - DisplayUtils.dp2px(this, 120)) {
            if (isKeyboardShowing) {
                LogUtils.e("点击事件" + "键盘在展示，要隐藏");
                KeyboardUtils.updateSoftInputMethod(this, WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                KeyboardUtils.hideKeyboard(getCurrentFocus());
            }
            if (isMultiLayoutShowing) {
                hideMultiLayout();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private static class MyHandler extends Handler {
        private WeakReference<ChatActivity> activityWeakReference;

        MyHandler(ChatActivity activity) {
            activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ChatActivity activity = activityWeakReference.get();
            if (activity != null) {
                switch (msg.what) {
                    case 0:
                        adapter.notifyDataSetChanged();
                        recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                        break;
                    case 1:
                        Bundle bundle = msg.getData();
                        String otherMsg = bundle.getString("otherMsg");
                        text_unread_msg.setVisibility(View.VISIBLE);
                        text_unread_msg.setText(otherMsg);
                        break;
                    case 2:
                        text_unread_msg.setVisibility(View.GONE);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /**
     * 展示多功能布局
     */
    private void showMultiLayout() {
        //更新多功能布局的高度和键盘高度相等
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) layout_multi.getLayoutParams();
        if (params != null) {
            params.height = AppConfig.getKeyboardHeight(ChatActivity.this);
            layout_multi.setLayoutParams(params);
        }

        //显示多功能布局，隐藏键盘
        layout_multi.setVisibility(View.VISIBLE);
        KeyboardUtils.updateSoftInputMethod(this, WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        KeyboardUtils.hideKeyboard(getCurrentFocus());
        isKeyboardShowing = false;
        isMultiLayoutShowing = true;
    }

    /**
     * 隐藏多功能布局
     */
    private void hideMultiLayout() {
        //把其他checkbox的选中状态取消
        if (-1 != checkedId) {
            CheckBox box = (CheckBox) findViewById(checkedId);
            box.setChecked(false);
            checkedId = -1;
        }

        layout_multi.setVisibility(View.GONE);
        KeyboardUtils.updateSoftInputMethod(this, WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        isMultiLayoutShowing = false;
    }

    private Runnable hideMultiLayoutRunnable = new Runnable() {
        @Override
        public void run() {
            hideMultiLayout();
        }
    };

    /**
     * 监听输入框的变化，根据字数变化切换按钮
     */
    private TextWatcher textWatcher = new TextWatcher() {
        private CharSequence sequence;

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            sequence = charSequence;
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (sequence.length() > 0) {
                imgbtn_send.setImageResource(R.mipmap.conversation_btn_messages_send);
                imgbtn_send.setClickable(true);
            } else {
                imgbtn_send.setImageResource(R.mipmap.conversation_btn_messages_send_disable);
                imgbtn_send.setClickable(false);
            }
        }
    };

    /**
     * 打开图片库
     */
    private void openImageGallery() {
        MultiImageSelector.create()
                .showCamera(true) // show camera or not. true by default
                .count(9) // max select image size, 9 by default. used width #.multi()
                .multi() // multi、single mode, default mode is multi;
                .start(this, REQUEST_IMAGE);
    }

    /**
     * 打开短视频录制页面
     */
    private void openRecordPage() {
        QupaiService qupaiService = QupaiManager.getQupaiService(this);
        if (qupaiService == null) {
            ToastUtils.showShort(ChatActivity.this, "插件没有初始化，无法获取 QupaiService");
            return;
        }
        qupaiService.showRecordPage(this, REQUEST_VIDEO, true);
    }
}
