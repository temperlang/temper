package lang.temper.log

import lang.temper.common.Log

interface LeveledMessageTemplate : MessageTemplateI {
    val suggestedLevel: Log.Level
}
