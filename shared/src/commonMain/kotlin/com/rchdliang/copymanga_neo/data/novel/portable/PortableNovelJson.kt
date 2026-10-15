package com.rchdliang.copymanga_neo.data.novel.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import kotlinx.serialization.json.*

object PortableNovelJson {
    fun text(o:JsonObject,key:String,fallback:String="")=(o[key] as? JsonPrimitive)?.contentOrNull?:fallback
    fun tags(o:JsonObject,key:String):List<NovelTag> =(o[key] as? JsonArray).orEmpty().map { value ->
        val row=value.jsonObject;NovelTag(text(row,"name"),text(row,"path_word"),(row["count"] as? JsonPrimitive)?.intOrNull)
    }
    fun themeCounts(o:JsonObject):List<NovelTag> {
        require(o["list"] is JsonArray){"小说题材目录缺少列表"}
        return tags(o,"list").onEach { require(it.name.isNotBlank()&&it.path.isNotBlank()&&it.count!=null&&it.count>=0){"小说题材目录缺少有效数量"} }
    }
    fun book(o:JsonObject)=NovelBook(text(o,"path_word"),text(o,"name"),text(o,"cover"),tags(o,"author"),tags(o,"theme"),text(o,"brief"),(o["status"] as? JsonObject)?.let { text(it,"display") }.orEmpty(),text(o,"datetime_updated"),uuid=text(o,"uuid"),popular=text(o,"popular").toIntOrNull())
    fun volume(o:JsonObject)=NovelVolume(text(o,"id"),text(o,"name"),(o["index"] as? JsonPrimitive)?.intOrNull?:0,text(o,"prev").takeIf{it.isNotBlank()},text(o,"next").takeIf{it.isNotBlank()})
    fun shelfPage(result:JsonObject,offset:Int):NovelShelfPage {
        require(result["offset"]?.jsonPrimitive?.int==offset){"小说书架分页位置无效"}
        val rows=requireNotNull(result["list"] as? JsonArray){"小说书架缺少列表"}
        val entries=rows.map { element->val row=element.jsonObject;val id=requireNotNull(row["uuid"]?.jsonPrimitive?.intOrNull){"小说收藏记录缺少 ID"};require(id>0){"小说收藏记录 ID 无效"};NovelShelfEntry(id,book(requireNotNull(row["book"] as? JsonObject))) }
        return NovelShelfPage(entries,requireNotNull(result["total"]).jsonPrimitive.int,offset,requireNotNull(result["limit"]).jsonPrimitive.int)
    }
    fun sections(o:JsonObject,doc:NovelTextDocument):List<NovelSection> = requireNotNull(o["contents"] as? JsonArray){"小说目录缺失"}.map { value->
        val row=value.jsonObject;val type=requireNotNull(row["content_type"]).jsonPrimitive.int;require(type==1||type==2){"未知的官方目录类型"}
        // Numeric fractions/overflows are rejected, never rounded into valid coordinates.
        fun line(key:String):Int?=(row[key] as? JsonPrimitive)?.contentOrNull?.let { raw -> exactInt(raw) }
        val start=if(type==1)requireNotNull(line("start_lines")) else line("start_lines")?:-1
        val end=if(type==1)requireNotNull(line("end_lines")) else line("end_lines")?:-1
        if(type==1)doc.validateRange(start,end)
        val image=text(row,"content").takeIf{it.isNotBlank()};if(type==2)require(image!=null){"插图地址缺失"}
        NovelSection(NovelTextDocument.sectionKey(text(o,"id"),type,start,end,image),text(row,"name"),type,start,end,image)
    }
    /** [metadataJson] is the API results object, not its enclosing code/results envelope. */
    fun parseReading(metadataJson:String,textBytes:ByteArray,cachedImages:Map<String,String> = emptyMap()):NovelReadingVolume {
        val result=Json.parseToJsonElement(metadataJson).jsonObject
        val volumeJson=requireNotNull(result["volume"] as? JsonObject){"小说卷信息缺失"}
        val book=book(requireNotNull(result["book"] as? JsonObject){"小说信息缺失"})
        val volume=volume(volumeJson)
        require(book.path.isNotBlank()&&volume.id.isNotBlank()){"小说缓存身份无效"}
        val document=NovelTextDocument.decode(textBytes,text(volumeJson,"txt_encoding"))
        val sections=sections(volumeJson,document)
        val urls=sections.filter{it.type==2}.map { requireNotNull(it.image) }.toSet()
        val images=cachedImages.filterKeys { it in urls }
        return NovelReadingVolume(book,volume,sections,document,images,images.size==urls.size)
    }
    internal fun exactInt(raw:String):Int? {
        val match=Regex("([+-]?)([0-9]*)(?:\\.([0-9]*))?(?:[eE]([+-]?[0-9]+))?").matchEntire(raw)?:return null
        val whole=match.groupValues[2];val fraction=match.groupValues[3]
        if(whole.isEmpty()&&fraction.isEmpty())return null
        val exponent=match.groupValues[4].let{if(it.isEmpty())0 else it.toIntOrNull()?:return null}
        var digits=(whole+fraction).trimStart('0')
        if(digits.isEmpty())return 0
        val scale=fraction.length.toLong()-exponent.toLong()
        if(scale>0) {
            if(scale>=digits.length||!digits.takeLast(scale.toInt()).all{it=='0'})return null
            digits=digits.dropLast(scale.toInt())
        }else if(scale<0) {
            if(digits.length-scale>10)return null
            digits+="0".repeat((-scale).toInt())
        }
        if(digits.length>10)return null
        return (match.groupValues[1]+digits).toIntOrNull()
    }
    fun bookJson(book:NovelBook)=buildJsonObject {
        put("path_word",book.path);put("name",book.name);put("cover",book.cover);put("brief",book.brief);put("datetime_updated",book.updated);put("uuid",book.uuid);book.popular?.let{put("popular",it)}
        fun values(tags:List<NovelTag>)=JsonArray(tags.map { buildJsonObject { put("name",it.name);put("path_word",it.path) } })
        put("author",values(book.authors));put("theme",values(book.themes));put("status",buildJsonObject{put("display",book.status)})
    }
}
