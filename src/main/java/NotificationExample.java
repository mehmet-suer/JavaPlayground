import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NotificationExample {

    public interface NotifierData {
        NotifyType getType();
    }

    public interface Notifier<T extends NotifierData> {
        void send(T payload);

        Class<T> getEntityType();
    }

    public enum NotifyType {
        EMAIL,
        SMS
    }


    public  record EmailNotificationData(String content) implements NotifierData {
        private static final NotifyType NOTIFY_TYPE = NotifyType.EMAIL;

        @Override
        public NotifyType getType() {
            return NOTIFY_TYPE;
        }
    }

    public record SmsNotificationData(String content) implements NotifierData {
        private static final NotifyType NOTIFY_TYPE = NotifyType.SMS;

        @Override
        public NotifyType getType() {
            return NOTIFY_TYPE;
        }
    }

    public static class EmailNotifier implements Notifier<EmailNotificationData> {

        @Override
        public void send(EmailNotificationData data) {
            System.out.println("email sent: " + data.content());
        }

        @Override
        public Class<EmailNotificationData> getEntityType() {
            return EmailNotificationData.class;
        }
    }

    public static class SMSNotifier implements Notifier<SmsNotificationData> {

        @Override
        public void send(SmsNotificationData data) {
            System.out.println("sms sent: " + data.content());
        }

        @Override
        public Class<SmsNotificationData> getEntityType() {
            return SmsNotificationData.class;
        }
    }


    public static class NotificationExecutor {
        private final Map<Class<? extends NotifierData>, Notifier<? extends NotifierData>> notifiers;

        public NotificationExecutor(List<Notifier<? extends NotifierData>> notifiers) {
            this.notifiers = notifiers.stream()
                    .collect(Collectors.toMap(
                                    Notifier::getEntityType,
                                    Function.identity()
                            )
                    );
        }

        @SuppressWarnings("unchecked")
        public <T extends NotifierData> void send(T notifierData) {
            Notifier<T> notifier = getNotifierForced(notifierData);
            notifier.send(notifierData);
        }

        @SuppressWarnings("unchecked")
        private <T extends NotifierData> Notifier<T> getNotifierForced(T payload) {
            Notifier<T> notifier = (Notifier<T>) notifiers.get(payload.getClass());
            if (notifier == null) {
                throw new IllegalArgumentException("No notifier found for type: " + payload.getClass());
            }
            return notifier;
        }


    }

    public static class NotificationExecutorService {

        private final NotificationExecutor executor;

        public NotificationExecutorService(EmailNotifier emailNotifier, SMSNotifier smsNotifier) {
            this.executor = new NotificationExecutor(List.of(emailNotifier, smsNotifier));
        }

        public void sendNotifications(List<NotifierData> payloads) {
            payloads.forEach(this.executor::send);
        }
    }


    public static class NotificationService {
        private final NotificationExecutorService notificationExecutorService;

        public NotificationService(NotificationExecutorService notificationExecutorFactory) {
            this.notificationExecutorService = notificationExecutorFactory;
        }

        public void sendNotifications(List<NotifierData> payloads) {
            this.notificationExecutorService.sendNotifications(payloads);
        }

    }


    public static void main(String[] args) {
        EmailNotifier emailNotifier = new EmailNotifier();
        SMSNotifier smsNotifier = new SMSNotifier();

        NotificationExecutorService executorService = new NotificationExecutorService(emailNotifier, smsNotifier);

        EmailNotificationData emailPayload = new EmailNotificationData("Email test data");
        SmsNotificationData smsPayload = new SmsNotificationData("Sms test Data");

        List<NotifierData> notifications = List.of(emailPayload, smsPayload);

        executorService.sendNotifications(notifications);
    }
}
