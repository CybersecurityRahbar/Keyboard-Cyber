# بحث بناء لوحة مفاتيح Android وتصميمها

تاريخ المراجعة: 14 نوفمبر 2026

هذه الملاحظات تحدد ما نستفيده من وثائق Android واللوحات المعروفة، دون نسخ تصميم أو كود أي منتج.

## 1. نموذج Android الرسمي

يوثق Android أن لوحة المفاتيح هي خدمة ترث من `InputMethodService`. عناصر الواجهة الرئيسية هي:

- **Input View:** منطقة المفاتيح والتفاعل الفعلي.
- **Candidates View:** منطقة مرشحات الكلمات/التصحيح. مشروعنا لا يحتاجها للتنبؤ التقليدي؛ بدلاً منها نعرض شريطاً مخصصاً لآخر مقتطفات الحافظة، ويمكن إخفاؤه من إعدادات المستخدم.

يجب على IME التعامل مع أنواع حقول الإدخال المختلفة (نص عادي، رقم، رابط، بريد، بحث، كلمة مرور)، وتغيير تخطيطها حسب نوع الحقل. يجب أن تتمدد الواجهة على أحجام الشاشات والاتجاهين، وألا تستهلك أكثر من نصف الشاشة في الوضع المعتاد بما يحجب سياق التطبيق المضيف.

**قرارنا:** الإبقاء على سلوك إدخال نص حقيقي عبر `InputMethodService` و`InputConnection`. واجهة التطبيق/الأرشيف مبنية بـ Compose؛ لوحة المفاتيح الحالية تستخدم Views خفيفة كمرحلة تشغيل أساسية. لا ننتقل إلى Compose داخل نافذة IME إلا مع إنشاء دورة حياة ومالكي ViewTree بشكل صحيح واختبار أثر ذلك على بدء اللوحة وسرعة اللمس.

## 2. ما تعلمناه من Microsoft SwiftKey

يوضح دليل Microsoft الرسمي أن شريط أدوات لوحة متقدمة يجمع أدوات مستقلة مثل:
- الحافظة.
- مبدّل اللغات والتخطيطات.
- المترجم.
- السمات والتخصيص.
- الرموز التعبيرية.
- أوضاع الاستخدام مثل يد واحدة والعائمة.

**قرارنا:** نستخدم شريط أدوات وحدات/أدوات قابل للفتح والإغلاق، لكن بترتيب وهوية بصرية خاصة بـ Keyboard Cyber. أهم الأدوات في مشروعنا: الأرشيف، الترجمة، اللغات والتخطيطات، الرموز، الإيموجي، والإعدادات. لا نضيف التصحيح التلقائي أو اقتراح الكلمات العادية لأن المستخدم لا يستخدمهما.

## 3. مشاريع مفتوحة المصدر للدراسة

- **FlorisBoard:** توثيقه يوضح إمكانية بناء كامل واجهة IME باستخدام Jetpack Compose، مع الحاجة إلى تجهيز Lifecycle/ViewTree owners لأن نافذة InputMethodService لا تأتي تلقائياً بمالكي Compose المعتادين. هذا نمط ندرسه عند مرحلة تطوير الواجهة؛ ليس هناك ضرورة لتعقيد النسخة الأولى به قبل ثبوت أساس IME.
- **HeliBoard:** مشروع مفتوح المصدر يركز على الخصوصية والتخصيص ولا يطلب إذن الإنترنت افتراضياً. نأخذ منه درس تقليل البيانات والوظائف الاختيارية. لا ننسخ كوداً منه؛ يجب مراجعة تراخيص أي مكوّن قبل إعادة استخدامه.

## 4. التصور التصميمي للوحة Cyber

الهوية المقترحة ليست رسماً مليئاً برموز الهاكر أو ألواناً نيوناً مزعجة، بل واجهة احترافية عملية:
- خلفية كحلية شديدة العمق، ومفاتيح طبقية ذات تباين واضح.
- لون تركوازي/أخضر تقني محدود للأزرار النشطة والمؤشرات، مع لون ثانوي أزرق للوظائف.
- مفاتيح مستديرة باعتدال، تمييز واضح بين مفاتيح الحروف ومفاتيح الوظائف، وحالات ضغط/لمس مرئية.
- شريط أدوات علوي رفيع، لا يزاحم مساحة الكتابة.
- شريط الحافظة يعرض مقتطفات محفوظة فقط، مع زر وصول للأرشيف الكامل.
- تخطيطات عربية RTL وإنجليزية LTR صحيحة، وعدم الاعتماد على تغيير ترتيب الصفوف وحده.
- صفحات منفصلة للأرقام/الرموز والإيموجي، مع عودة سهلة إلى الحروف.
- تكيّف مع العرض والارتفاع، landscape، واختلاف كثافة الشاشة.
- إعدادات ارتفاع اللوحة، الاهتزاز عند اللمس، صوت المفاتيح، ومظهر المفاتيح تأتي لاحقاً بعد اختبار الاستخدام.

## 5. مراحل التنفيذ المستندة إلى البحث

1. تثبيت خدمة IME والكتابة والحذف والمسافة والإدخال وتبديل التخطيطات.
2. اختبار التفاعل في أنواع الحقول المختلفة قبل تحسين الرسوم.
3. بناء طبقة Layout Registry منفصلة عن العرض لتسهيل إضافة أكثر من 50 لغة على مراحل.
4. تحويل شريط الأدوات إلى أدوات مستقلة: Archive / Translate / Languages / Symbols / Emoji.
5. تحسين touch feedback، الضغط المطوّل، backspace المتكرر، والتعامل الصحيح مع Unicode/emoji.
6. اختبار أبعاد الشاشة والاتجاهين والتطبيقات المضيفة.
7. بعد استقرار الأساس، اختيار ما إذا كان IME UI سيبقى Native View أو ينتقل إلى Compose مع Lifecycle bridge مدروس.

## 6. مراجع

- Android Developers — Create an input method:
  https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- Android Developers — InputMethodService:
  https://developer.android.com/reference/android/inputmethodservice/InputMethodService
- Android Developers — InputConnection:
  https://developer.android.com/reference/android/view/inputmethod/InputConnection
- Microsoft SwiftKey — Toolbar contents and access:
  https://support.microsoft.com/en-gb/swiftkey-keyboard/how-do-i-access-toolbar-in-microsoft-swiftkey-keyboard
- Microsoft SwiftKey — Themes:
  https://support.microsoft.com/en-us/swiftkey-keyboard/which-themes-are-available-for-my-microsoft-swiftkey-keyboard
- FlorisBoard technical documentation — custom UI:
  https://bramburn.github.io/florisboard/docs/technical/custom-ui
- FlorisBoard documentation:
  https://bramburn.github.io/florisboard/docs
- HeliBoard source repository:
  https://github.com/HeliBorg/HeliBoard
