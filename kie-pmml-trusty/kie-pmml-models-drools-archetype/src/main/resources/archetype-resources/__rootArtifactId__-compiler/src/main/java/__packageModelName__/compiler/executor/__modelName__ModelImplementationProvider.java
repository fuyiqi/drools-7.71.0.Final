#set($str="")
#set($dt=$str.getClass().forName("java.util.Date").newInstance())
#set($year=$dt.getYear()+1900)
/*
* Copyright ${year} Red Hat, Inc. and/or its affiliates.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package ${package}.${packageModelName}.compiler.executor;

import java.util.List;
import java.util.Map;

import org.dmg.pmml.Field;
import org.dmg.pmml.${packageModelName}.${modelName}Model;
import org.kie.pmml.api.enums.PMML_MODEL;
import org.kie.pmml.api.exceptions.KiePMMLException;
import org.kie.pmml.models.drools.ast.KiePMMLDroolsAST;
import org.kie.pmml.models.drools.ast.KiePMMLDroolsType;
import org.kie.pmml.models.drools.dto.DroolsCompilationDTO;
import org.kie.pmml.models.drools.provider.DroolsModelProvider;
import ${package}.${packageModelName}.compiler.factories.KiePMML${modelName}ModelFactory;
import ${package}.${packageModelName}.model.KiePMML${modelName}Model;
import org.kie.pmml.models.drools.tuples.KiePMMLOriginalTypeGeneratedType;

import static ${package}.${packageModelName}.model.KiePMML${modelName}Model.PMML_MODEL_TYPE;

/**
 * Default <code>DroolsModelProvider</code> for <b>${modelName}</b>
 */
public class ${modelName}ModelImplementationProvider extends DroolsModelProvider<${modelName}Model,KiePMML${modelName}Model>{

    @Override
    public PMML_MODEL getPMMLModelType() {
        return PMML_MODEL_TYPE;
    }

    @Override
    public KiePMML${modelName}Model getKiePMMLDroolsModel(final DroolsCompilationDTO<${modelName}Model> compilationDTO) {
        try {
            return KiePMML${modelName}ModelFactory.getKiePMML${modelName}Model(compilationDTO);
        } catch (IllegalAccessException | InstantiationException e) {
            throw new KiePMMLException(e.getMessage(), e);
        }
    }

    @Override
    public KiePMMLDroolsAST getKiePMMLDroolsAST(final List<Field<?>> fields,
                                                final ${modelName}Model model,
                                                final Map<String, KiePMMLOriginalTypeGeneratedType> fieldTypeMap,
                                                final List<KiePMMLDroolsType> types) {
        return KiePMML${modelName}ModelFactory.getKiePMMLDroolsAST(fields, model, fieldTypeMap, types);
    }

    @Override
    public Map<String, String> getKiePMMLDroolsModelSourcesMap(final DroolsCompilationDTO<${modelName}Model> compilationDTO) {
        return KiePMML${modelName}ModelFactory.getKiePMML${modelName}ModelSourcesMap(compilationDTO);
    }
}
