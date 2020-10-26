# ---
# 
# Reorder as follow before applying conversion
# 1. <master>
# 2. <mode> | <class> | <name> | <spark-opts> | 
# 3. <jar>
# 4. <arg>
#
# ---

var_input_file="$1"
echo "DEBUG: var_input_file=${var_input_file}"

var_processed_file="${var_input_file}".processed.xml
echo "DEBUG: var_processed_file=${var_processed_file}"

var_file_tag_content='hdfs:\/\/\/BDDF\/Projet\/SNB\/pfm\/Scripts\/oozie\/shared\/lib\/run-spark-submit.sh#run-spark-submit.sh'

echo '# Processing'
cp -vf "${var_input_file}" "${var_processed_file}"

# <spark-opts> newlines
sed -i '$!N;s/<spark-opts>\(\s\|\n\|\t\)*/<spark-opts>/g;P;D' "${var_processed_file}"
sed -i '$!N;s/\(\s\|\n\|\t\)*<\/spark-opts>/<\/spark-opts>/g;P;D' "${var_processed_file}"
# echo -e '<spark-opts>    \n--conf a --conf b\n   </spark-opts>' |sed '$!N;s/<spark-opts>\(\s\|\n\|\t\)*/<spark-opts>/g;P;D' |sed '$!N;s/\(\s\|\n\|\t\)*<\/spark-opts>/<\/spark-opts>/g;P;D'

arg_begin='<argument>'
arg_end='<\/argument>'

sed -i "s/<spark.*xmlns=\".*\"/<shell xmlns=\"uri:oozie:shell-action:0.2\"/g" "${var_processed_file}"
sed -i 's/<\/spark>/<\/shell>/g' "${var_processed_file}"

# spark-submit tags
declare -a tags=(
"master:--master"
"mode:--deploy-mode"
"name:--name"
"class:--class"
"spark-opts:"
"jar:"
"arg:"
)
for tag in "${tags[@]}"; do
    k="$(echo ${tag} |cut -d':' -f1)"
    v="$(echo ${tag} |cut -d':' -f2)"
    if [ -z "$v" ]; then v=''; else v="$v "; fi
    sed -i "s/<$k>\(.*\)<\/$k>/${arg_begin}$v\1${arg_end}/g" "${var_processed_file}"
done

# SPARK_MAJOR_VERSION=2 just before </shell>
sed -i "s/<\/shell>/\t<env-var>SPARK_MAJOR_VERSION=2<\/env-var>\n\t\t<\/shell>/g" "${var_processed_file}"

# <file> just before </shell>
sed -i "s/<\/shell>/\t<file>${var_file_tag_content}<\/file>\n\t\t<\/shell>/g" "${var_processed_file}"
